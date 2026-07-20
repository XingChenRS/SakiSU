#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/elf.h>
#include <linux/string.h>
#include <linux/fs.h>
#include <linux/file.h>

#include "arch.h"
#include "klog.h" // IWYU pragma: keep
#include "hook/syscall_hook.h"
#include "hook/init_module_filter.h"
#include "compat/kernel_compat.h"

// Bounds for ELF metadata parsing. A kernel module's ELF header, section
// table, section-name string table and .modinfo are all small; the module
// body (text/data) can be hundreds of KiB, but we never read the whole
// image -- only the pieces needed to extract the module name.
#define KSU_MAX_SECTIONS 512
#define KSU_MAX_SHSTRTAB (64 * 1024)
#define KSU_MAX_MODINFO (16 * 1024)

// Abstract source of an ELF module image. init_module(2) passes a userspace
// buffer; finit_module(2) passes a file descriptor. Both are read at explicit
// offsets so we never have to slurp the entire (large) module into memory.
struct ksu_elf_reader {
    const char __user *umod; // init_module source, NULL for the file path
    unsigned long umod_len;
    struct file *file; // finit_module source, NULL for the user path
    loff_t file_size;
};

// Read exactly @len bytes at @offset into @buf, with full bounds checking.
// Returns 0 on success, negative on any error (so callers fail open).
static int ksu_reader_read(struct ksu_elf_reader *r, loff_t offset, void *buf, size_t len)
{
    loff_t total = r->umod ? (loff_t)r->umod_len : r->file_size;

    if (len == 0)
        return -EINVAL;
    if (offset < 0 || (loff_t)len > total || offset > total - (loff_t)len)
        return -ERANGE;

    if (r->umod) {
        if (copy_from_user(buf, r->umod + offset, len))
            return -EFAULT;
        return 0;
    }

    loff_t pos = offset;
    ssize_t n = ksu_kernel_read_compat(r->file, buf, len, &pos);
    if (n < 0 || (size_t)n != len)
        return -EIO;
    return 0;
}

// Returns true iff the ELF module's declared name (.modinfo "name=" entry)
// is exactly "vr". Any parse failure, missing section, or mismatch returns
// false so the original syscall proceeds unchanged.
static bool ksu_module_is_vr(struct ksu_elf_reader *r)
{
    Elf64_Ehdr ehdr;
    Elf64_Shdr *shdrs = NULL;
    char *shstrtab = NULL;
    char *modinfo = NULL;
    Elf64_Shdr *shstr_sh;
    Elf64_Shdr *modinfo_sh = NULL;
    unsigned int shnum, i;
    unsigned long shtab_bytes;
    bool is_vr = false;

    if (ksu_reader_read(r, 0, &ehdr, sizeof(ehdr)))
        return false;

    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0)
        return false;
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64)
        return false;
    if (ehdr.e_type != ET_REL)
        return false;
    if (ehdr.e_shentsize != sizeof(Elf64_Shdr))
        return false;

    shnum = ehdr.e_shnum;
    if (shnum == 0 || shnum > KSU_MAX_SECTIONS)
        return false;
    if (ehdr.e_shstrndx >= shnum)
        return false;

    shtab_bytes = (unsigned long)shnum * sizeof(Elf64_Shdr);
    shdrs = kmalloc(shtab_bytes, GFP_KERNEL);
    if (!shdrs)
        return false;
    if (ksu_reader_read(r, ehdr.e_shoff, shdrs, shtab_bytes))
        goto out;

    // Read the section-name string table.
    shstr_sh = &shdrs[ehdr.e_shstrndx];
    if (shstr_sh->sh_size == 0 || shstr_sh->sh_size > KSU_MAX_SHSTRTAB)
        goto out;
    shstrtab = kmalloc(shstr_sh->sh_size, GFP_KERNEL);
    if (!shstrtab)
        goto out;
    if (ksu_reader_read(r, shstr_sh->sh_offset, shstrtab, shstr_sh->sh_size))
        goto out;

    // Locate the .modinfo section.
    for (i = 0; i < shnum; i++) {
        Elf64_Shdr *sh = &shdrs[i];
        const char *name;
        unsigned long remaining;

        if (sh->sh_name >= shstr_sh->sh_size)
            continue;
        name = shstrtab + sh->sh_name;
        remaining = shstr_sh->sh_size - sh->sh_name;
        if (strnlen(name, remaining) >= remaining)
            continue; // not NUL-terminated within bounds
        if (strcmp(name, ".modinfo") == 0) {
            modinfo_sh = sh;
            break;
        }
    }
    if (!modinfo_sh)
        goto out;

    if (modinfo_sh->sh_size == 0 || modinfo_sh->sh_size > KSU_MAX_MODINFO)
        goto out;
    modinfo = kmalloc(modinfo_sh->sh_size, GFP_KERNEL);
    if (!modinfo)
        goto out;
    if (ksu_reader_read(r, modinfo_sh->sh_offset, modinfo, modinfo_sh->sh_size))
        goto out;

    // .modinfo is a sequence of NUL-separated "key=value" entries. Find the
    // "name=" entry and compare its value against "vr".
    {
        const char *p = modinfo;
        const char *end = modinfo + modinfo_sh->sh_size;

        while (p < end) {
            const char *nul = memchr(p, '\0', end - p);
            size_t entlen;

            if (!nul)
                break;
            entlen = nul - p;
            if (entlen > 5 && memcmp(p, "name=", 5) == 0) {
                const char *val = p + 5;
                size_t vlen = entlen - 5;

                if (vlen == 2 && val[0] == 'v' && val[1] == 'r')
                    is_vr = true;
                break; // module name is unique; stop after the first name=
            }
            p = nul + 1;
        }
    }

out:
    kfree(modinfo);
    kfree(shstrtab);
    kfree(shdrs);
    return is_vr;
}

// init_module(2): sys_init_module(void __user *umod, unsigned long len,
//                                 const char __user *uargs)
static long (*orig_sys_init_module)(const struct pt_regs *regs);
static long ksu_sys_init_module(const struct pt_regs *regs)
{
    struct ksu_elf_reader r = {
        .umod = (const char __user *)PT_REGS_PARM1(regs),
        .umod_len = (unsigned long)PT_REGS_PARM2(regs),
        .file = NULL,
        .file_size = 0,
    };

    if (r.umod && r.umod_len >= sizeof(Elf64_Ehdr) && ksu_module_is_vr(&r)) {
        pr_info("init_module_filter: blocked vr (init_module)\n");
        return 0;
    }

    return orig_sys_init_module(regs);
}

// finit_module(2): sys_finit_module(int fd, const char __user *uargs, int flags)
static long (*orig_sys_finit_module)(const struct pt_regs *regs);
static long ksu_sys_finit_module(const struct pt_regs *regs)
{
    int fd = (int)PT_REGS_PARM1(regs);
    struct file *file = fget(fd);
    bool is_vr = false;

    if (file) {
        struct ksu_elf_reader r = {
            .umod = NULL,
            .umod_len = 0,
            .file = file,
            .file_size = i_size_read(file_inode(file)),
        };

        if (r.file_size >= (loff_t)sizeof(Elf64_Ehdr))
            is_vr = ksu_module_is_vr(&r);
        fput(file);
    }

    if (is_vr) {
        pr_info("init_module_filter: blocked vr (finit_module)\n");
        return 0;
    }

    return orig_sys_finit_module(regs);
}

void __init ksu_init_module_filter_init(void)
{
#ifdef __aarch64__
    // Direct syscall-table patching (NOT the dispatcher API): vr.ko is loaded
    // by vendor init, which is never tracepoint-marked, so a dispatcher hook
    // would never see it. Patching the table intercepts every process.
    ksu_syscall_table_hook(__NR_init_module, ksu_sys_init_module, &orig_sys_init_module);
    ksu_syscall_table_hook(__NR_finit_module, ksu_sys_finit_module, &orig_sys_finit_module);
    pr_info("init_module_filter: hooked init_module + finit_module\n");
#else
    pr_info("init_module_filter: skipped (not arm64)\n");
#endif
}

void __exit ksu_init_module_filter_exit(void)
{
#ifdef __aarch64__
    ksu_syscall_table_unhook(__NR_init_module);
    ksu_syscall_table_unhook(__NR_finit_module);
    pr_info("init_module_filter: unhooked\n");
#endif
}
