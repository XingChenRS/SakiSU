use anyhow::{Context, Result};
use goblin::elf::{Elf, section_header, sym::Sym};
use scroll::{Pwrite, ctx::SizeWith};
use std::collections::HashMap;
use std::ffi::CStr;
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read, Seek, SeekFrom};
use syscalls::{Sysno, syscall};

struct Kptr {
    value: String,
}

impl Kptr {
    pub fn new() -> Result<Self> {
        let value = fs::read_to_string("/proc/sys/kernel/kptr_restrict")?;
        fs::write("/proc/sys/kernel/kptr_restrict", "1")?;
        Ok(Kptr { value })
    }
}

impl Drop for Kptr {
    fn drop(&mut self) {
        let _ = fs::write("/proc/sys/kernel/kptr_restrict", self.value.as_bytes());
    }
}

pub struct KptrOwnedIter<I> {
    _kptr: Kptr,
    iter: I,
}

impl<I: Iterator> Iterator for KptrOwnedIter<I> {
    type Item = I::Item;

    fn next(&mut self) -> Option<Self::Item> {
        self.iter.next()
    }
}

pub fn kernel_symbols_iter() -> Result<impl Iterator<Item = (String, u64)>> {
    let kptr = Kptr::new()?;

    let iter = BufReader::new(File::open("/proc/kallsyms")?)
        .lines()
        // https://github.com/torvalds/linux/blob/7f87a5ea75f011d2c9bc8ac0167e5e2d1adb1594/kernel/kallsyms.c#L727
        // We can stop read as soon as we read all kernel symbols
        .map_while(|line| {
            line.ok().and_then(|line| {
                let mut splits = line.split_whitespace();
                splits
                    .next()
                    .and_then(|addr| u64::from_str_radix(addr, 16).ok())
                    .and_then(|addr| {
                        splits
                            .nth(1)
                            .take_if(|_| splits.next().is_none()) // stop at module symbols
                            .map(|symbol| {
                                (
                                    symbol
                                        .find("$")
                                        .or_else(|| symbol.find(".llvm."))
                                        .map(|pos| &symbol[0..pos])
                                        .unwrap_or(symbol)
                                        .to_owned(),
                                    addr,
                                )
                            })
                    })
            })
        });

    Ok(KptrOwnedIter { _kptr: kptr, iter })
}

pub fn for_each_kernel_symbols<F: FnMut(&(String, u64)) -> Result<bool>>(mut f: F) -> Result<()> {
    for item in kernel_symbols_iter()? {
        if !f(&item)? {
            break;
        }
    }
    Ok(())
}

/// Relocate undefined symbols in an ELF kernel module buffer using /proc/kallsyms,
/// then load it via init_module syscall.
pub fn load_module(data: &[u8], params: &CStr) -> Result<()> {
    let mut buffer = data.to_vec();
    let elf = Elf::parse(&buffer)?;
    let ctx = *elf.syms.ctx();

    let mut unresolved_symbols: HashMap<String, (Sym, usize)> = HashMap::new();
    for (index, sym) in elf.syms.iter().enumerate() {
        if index == 0 {
            continue;
        }

        if sym.st_shndx != section_header::SHN_UNDEF as usize {
            continue;
        }

        let Some(name) = elf.strtab.get_at(sym.st_name) else {
            continue;
        };

        let offset = elf.syms.offset() + index * Sym::size_with(elf.syms.ctx());
        unresolved_symbols.insert(name.to_owned(), (sym, offset));
    }

    if !unresolved_symbols.is_empty() {
        for_each_kernel_symbols(|(symbol, addr)| {
            if let Some((mut sym, offset)) = unresolved_symbols.remove(symbol) {
                sym.st_shndx = section_header::SHN_ABS as usize;
                sym.st_value = *addr;
                buffer.pwrite_with(sym, offset, ctx)?;
            }

            Ok(!unresolved_symbols.is_empty())
        })
        .context("Cannot parse kallsyms")?;
    }

    for name in unresolved_symbols.keys() {
        log::warn!("Cannot find symbol: {}", name);
    }

    // Try init_module with vermagic mismatch fallback
    try_load_with_vermagic_fallback(&mut buffer, params)?;
    Ok(())
}

/// Try init_module, with vermagic mismatch fallback on first failure.
fn try_load_with_vermagic_fallback(buffer: &mut Vec<u8>, params: &CStr) -> Result<()> {
    // First attempt
    let result = unsafe {
        syscall!(
            Sysno::init_module,
            buffer.as_ptr(),
            buffer.len(),
            params.as_ptr()
        )
    };

    match result {
        Ok(_) => return Ok(()),
        Err(e) => {
            log::warn!("init_module failed on first attempt: {:?}", e);

            // Only try fallback if we can capture kmsg
            match try_vermagic_fallback(buffer, params) {
                Ok(_) => return Ok(()),
                Err(fallback_err) => {
                    return Err(anyhow::anyhow!(
                        "init_module failed: {:?}; fallback: {:?}",
                        e,
                        fallback_err
                    ));
                }
            }
        }
    }
}

/// Try to recover from vermagic mismatch by reading kmsg and patching module.
fn try_vermagic_fallback(buffer: &mut Vec<u8>, params: &CStr) -> Result<()> {
    // Open /dev/kmsg or /kmsg
    let mut kmsg_file = fs::OpenOptions::new()
        .read(true)
        .open("/dev/kmsg")
        .or_else(|_| fs::OpenOptions::new().read(true).open("/kmsg"))
        .context("Cannot open kmsg")?;

    // Seek to end to capture only new messages
    kmsg_file
        .seek(SeekFrom::End(0))
        .context("Cannot seek kmsg")?;

    // Read recent kernel messages (limit to 16KB)
    let mut recent_log = String::new();
    let mut limited_reader = kmsg_file.take(16384);
    limited_reader
        .read_to_string(&mut recent_log)
        .context("Cannot read kmsg")?;

    // Look for version magic mismatch pattern in reverse
    let required_vermagic = recent_log
        .lines()
        .rev()
        .find_map(|line| {
            if line.contains("version magic") && line.contains("should be") {
                // Extract "should be '<vermagic>'" portion
                line.split("should be '")
                    .nth(1)
                    .and_then(|s| s.split('\'').next())
                    .map(|s| s.to_string())
            } else {
                None
            }
        })
        .context("No version magic mismatch found in kmsg")?;

    log::warn!(
        "Kernel requires vermagic {:?}; patching module and retrying",
        required_vermagic
    );

    // Parse ELF and replace vermagic in .modinfo
    replace_module_vermagic(buffer, &required_vermagic)
        .context("Cannot replace module vermagic")?;

    // Retry with patched module
    let result = unsafe {
        syscall!(
            Sysno::init_module,
            buffer.as_ptr(),
            buffer.len(),
            params.as_ptr()
        )
    };

    result.map(|_| ()).map_err(|e| {
        anyhow::anyhow!("init_module failed after vermagic replacement: {:?}", e)
    })
}

/// Replace vermagic= entry in module's .modinfo section.
fn replace_module_vermagic(buffer: &mut Vec<u8>, new_vermagic: &str) -> Result<()> {
    // First pass: extract all metadata before mutating buffer
    let elf = Elf::parse(&*buffer).context("Invalid ELF")?;

    // Only support ELF64 relocatable
    if !elf.is_64 || elf.header.e_type != goblin::elf::header::ET_REL {
        anyhow::bail!("Module must be ELF64 relocatable");
    }

    // Find .modinfo section
    let modinfo_section = elf
        .section_headers
        .iter()
        .find(|sh| {
            elf.shdr_strtab
                .get_at(sh.sh_name)
                .map_or(false, |name| name == ".modinfo")
        })
        .context("No .modinfo section")?;

    let offset = modinfo_section.sh_offset as usize;
    let size = modinfo_section.sh_size as usize;
    let align = modinfo_section.sh_addralign as usize;
    let shoff = elf.header.e_shoff as usize;
    let shentsize = elf.header.e_shentsize as usize;
    let modinfo_idx = elf
        .section_headers
        .iter()
        .position(|sh| sh == modinfo_section)
        .context("Cannot find .modinfo index")?;

    if offset.checked_add(size).map_or(true, |end| end > buffer.len()) {
        anyhow::bail!(".modinfo section out of bounds");
    }

    let modinfo_data = &buffer[offset..offset + size];

    // Parse NUL-separated key=value entries
    let entries: Vec<Vec<u8>> = modinfo_data
        .split(|&b| b == 0)
        .filter(|e| !e.is_empty())
        .map(|e| e.to_vec())
        .collect();

    // Find vermagic= entry
    let vermagic_prefix = b"vermagic=";
    if !entries.iter().any(|e| e.starts_with(vermagic_prefix)) {
        anyhow::bail!("No vermagic= entry in .modinfo");
    }

    // Build replacement
    let new_entry = format!("vermagic={}", new_vermagic);
    let new_entry_bytes = new_entry.into_bytes();

    // Rebuild .modinfo: replace old entry with new, keep others
    let mut new_modinfo = Vec::new();
    for entry in &entries {
        if entry.starts_with(vermagic_prefix) {
            new_modinfo.extend_from_slice(&new_entry_bytes);
        } else {
            new_modinfo.extend_from_slice(entry);
        }
        new_modinfo.push(0); // NUL terminator
    }

    // Align to section alignment
    if align > 1 {
        let padding = (align - (new_modinfo.len() % align)) % align;
        new_modinfo.resize(new_modinfo.len() + padding, 0);
    }

    // Now mutate buffer: replace .modinfo
    buffer.splice(offset..offset + size, new_modinfo.iter().cloned());

    // Update section header size in ELF
    let new_size = new_modinfo.len() as u64;
    let sh_size_offset = shoff + modinfo_idx * shentsize + 32; // sh_size is at +32 in Elf64_Shdr
    if sh_size_offset + 8 > buffer.len() {
        anyhow::bail!("Cannot update section header: out of bounds");
    }

    buffer[sh_size_offset..sh_size_offset + 8].copy_from_slice(&new_size.to_le_bytes());

    Ok(())
}

fn has_kernelsu_legacy() -> bool {
    use syscalls::{Sysno, syscall};
    let mut version = 0;
    const CMD_GET_VERSION: i32 = 2;
    unsafe {
        let _ = syscall!(
            Sysno::prctl,
            0xDEADBEEF,
            CMD_GET_VERSION,
            std::ptr::addr_of_mut!(version)
        );
    }

    log::info!("KernelSU version: {}", version);

    version != 0
}

fn has_kernelsu_v2() -> bool {
    use syscalls::{Sysno, syscall};
    const KSU_INSTALL_MAGIC1: u32 = 0xDEADBEEF;
    const KSU_INSTALL_MAGIC2: u32 = 0xCAFEBABE;
    const KSU_IOCTL_GET_INFO: u32 = 0x80104b02; // _IOR('K', 2, struct ksu_get_info_cmd)
    const KSU_IOCTL_GET_INFO_LEGACY: u32 = 0x80004b02; // _IOC(_IOC_READ, 'K', 2, 0)

    #[repr(C)]
    #[derive(Default)]
    struct GetInfoCmd {
        version: u32,
        flags: u32,
        features: u32,
        uapi_version: u32,
    }

    #[repr(C)]
    #[derive(Default)]
    struct GetInfoLegacyCmd {
        version: u32,
        flags: u32,
        features: u32,
    }

    // Try new method: get driver fd using reboot syscall with magic numbers
    let mut fd: i32 = -1;
    unsafe {
        let _ = syscall!(
            Sysno::reboot,
            KSU_INSTALL_MAGIC1,
            KSU_INSTALL_MAGIC2,
            0,
            std::ptr::addr_of_mut!(fd)
        );
    }

    let version = if fd >= 0 {
        // New method: try to get version info via ioctl
        let mut cmd = GetInfoCmd::default();
        let version = unsafe {
            let ret = syscall!(Sysno::ioctl, fd, KSU_IOCTL_GET_INFO, &mut cmd as *mut _);

            match ret {
                Ok(_) => cmd.version,
                Err(_) => {
                    let mut cmd = GetInfoLegacyCmd::default();
                    match syscall!(
                        Sysno::ioctl,
                        fd,
                        KSU_IOCTL_GET_INFO_LEGACY,
                        &mut cmd as *mut _
                    ) {
                        Ok(_) => cmd.version,
                        Err(_) => 0,
                    }
                }
            }
        };

        unsafe {
            let _ = syscall!(Sysno::close, fd);
        }

        version
    } else {
        0
    };

    log::info!("KernelSU version: {}", version);

    version != 0
}

pub fn has_kernelsu() -> bool {
    has_kernelsu_v2() || has_kernelsu_legacy()
}
