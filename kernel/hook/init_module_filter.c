#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/elf.h>
#include <linux/string.h>

#include "arch.h"
#include "klog.h"
#include "hook/syscall_hook.h"
#include "hook/init_module_filter.h"

// Maximum size to read from user for ELF parsing (64KB)
#define MAX_MODULE_HEADER_SIZE (64 * 1024)

// Maximum .modinfo section size to parse (16KB)
#define MAX_MODINFO_SIZE (16 * 1024)

// Maximum module name length (Linux kernel uses 56 for module->name)
#define MODULE_NAME_LEN 64

/**
 * Extract module name from ELF .modinfo section.
 * Returns 0 on success, negative on error.
 * name_out must have at least MODULE_NAME_LEN bytes.
 */
static int extract_module_name(const void *elf_data, unsigned long len, char *name_out, size_t name_out_size)
{
    const Elf64_Ehdr *ehdr;
    const Elf64_Shdr *shdr;
    const char *shstrtab;
    const Elf64_Shdr *shstrtab_shdr;
    const Elf64_Shdr *modinfo_shdr = NULL;
    const char *modinfo_data;
    unsigned long modinfo_size;
    unsigned long i;
    const char *p, *end;

    // Validate minimum size
    if (len < sizeof(Elf64_Ehdr)) {
        return -EINVAL;
    }

    ehdr = (const Elf64_Ehdr *)elf_data;

    // Validate ELF magic
    if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) {
        return -EINVAL;
    }

    // Only support ELF64
    if (ehdr->e_ident[EI_CLASS] != ELFCLASS64) {
        return -EINVAL;
    }

    // Only support ET_REL (relocatable)
    if (ehdr->e_type != ET_REL) {
        return -EINVAL;
    }

    // Validate section header table
    if (ehdr->e_shoff == 0 || ehdr->e_shnum == 0 || ehdr->e_shentsize != sizeof(Elf64_Shdr)) {
        return -EINVAL;
    }

    // Check section header table bounds
    if (ehdr->e_shoff > len ||
        ehdr->e_shoff + (unsigned long)ehdr->e_shnum * sizeof(Elf64_Shdr) > len) {
        return -ERANGE;
    }

    // Validate section name string table index
    if (ehdr->e_shstrndx >= ehdr->e_shnum) {
        return -EINVAL;
    }

    shdr = (const Elf64_Shdr *)((const char *)elf_data + ehdr->e_shoff);
    shstrtab_shdr = &shdr[ehdr->e_shstrndx];

    // Validate section name string table bounds
    if (shstrtab_shdr->sh_offset > len ||
        shstrtab_shdr->sh_offset + shstrtab_shdr->sh_size > len) {
        return -ERANGE;
    }

    shstrtab = (const char *)elf_data + shstrtab_shdr->sh_offset;

    // Find .modinfo section
    for (i = 0; i < ehdr->e_shnum; i++) {
        const Elf64_Shdr *sh = &shdr[i];
        const char *name;

        // Validate section name offset
        if (sh->sh_name >= shstrtab_shdr->sh_size) {
            continue;
        }

        name = shstrtab + sh->sh_name;

        // Check for NUL termination within bounds
        if (strnlen(name, shstrtab_shdr->sh_size - sh->sh_name) >= shstrtab_shdr->sh_size - sh->sh_name) {
            continue;
        }

        if (strcmp(name, ".modinfo") == 0) {
            modinfo_shdr = sh;
            break;
        }
    }

    if (!modinfo_shdr) {
        return -ENOENT;
    }

    // Validate .modinfo section bounds
    if (modinfo_shdr->sh_offset > len ||
        modinfo_shdr->sh_offset + modinfo_shdr->sh_size > len) {
        return -ERANGE;
    }

    modinfo_data = (const char *)elf_data + modinfo_shdr->sh_offset;
    modinfo_size = modinfo_shdr->sh_size;

    // Limit parsing size
    if (modinfo_size > MAX_MODINFO_SIZE) {
        modinfo_size = MAX_MODINFO_SIZE;
    }

    // Parse NUL-separated key=value entries
    p = modinfo_data;
    end = modinfo_data + modinfo_size;

    while (p < end) {
        const char *entry_start = p;
        const char *entry_end;
        size_t entry_len;

        // Find NUL terminator
        entry_end = memchr(p, '\0', end - p);
        if (!entry_end) {
            // No more entries
            break;
        }

        entry_len = entry_end - entry_start;

        // Check for "name=" prefix
        if (entry_len > 5 && memcmp(entry_start, "name=", 5) == 0) {
            const char *value = entry_start + 5;
            size_t value_len = entry_len - 5;

            if (value_len >= name_out_size) {
                return -ENAMETOOLONG;
            }

            memcpy(name_out, value, value_len);
            name_out[value_len] = '\0';
            return 0;
        }

        // Move to next entry
        p = entry_end + 1;
    }

    // name= not found
    return -ENOENT;
}

/**
 * Syscall hook for init_module.
 * Blocks loading of modules named "vr".
 */
static long ksu_hook_init_module(int orig_nr, const struct pt_regs *regs)
{
    void __user *umod;
    unsigned long len;
    void *kernel_buf = NULL;
    char module_name[MODULE_NAME_LEN];
    int ret;

    // Extract arguments from registers
    // arm64: init_module(void *umod, unsigned long len, const char *uargs)
    // regs[0] = umod, regs[1] = len, regs[2] = uargs
    umod = (void __user *)PT_REGS_PARM1(regs);
    len = (unsigned long)PT_REGS_PARM2(regs);

    // Sanity check length
    if (len == 0 || len > MAX_MODULE_HEADER_SIZE) {
        // Too small or too large, let kernel handle it
        goto call_original;
    }

    // Allocate kernel buffer for parsing
    kernel_buf = kmalloc(len, GFP_KERNEL);
    if (!kernel_buf) {
        // Allocation failed, let kernel handle it
        goto call_original;
    }

    // Copy module from user space
    if (copy_from_user(kernel_buf, umod, len)) {
        // Copy failed, let kernel handle it
        goto cleanup_and_call_original;
    }

    // Extract module name
    ret = extract_module_name(kernel_buf, len, module_name, sizeof(module_name));
    if (ret < 0) {
        // Extraction failed, let kernel handle it
        goto cleanup_and_call_original;
    }

    // Check if this is "vr" module
    if (strcmp(module_name, "vr") == 0) {
        pr_info("init_module_filter: blocked vr module load\n");
        kfree(kernel_buf);
        // Return success to fake load
        return 0;
    }

    // Not vr, proceed normally
cleanup_and_call_original:
    kfree(kernel_buf);

call_original:
    // Call original syscall
    return ksu_syscall_table[orig_nr](regs);
}

void __init ksu_init_module_filter_init(void)
{
#ifdef __aarch64__
    int ret = ksu_register_syscall_hook(__NR_init_module, ksu_hook_init_module);
    if (ret == 0) {
        pr_info("init_module_filter: registered for __NR_init_module\n");
    } else {
        pr_err("init_module_filter: failed to register hook: %d\n", ret);
    }
#else
    pr_info("init_module_filter: skipped (not arm64)\n");
#endif
}

void __exit ksu_init_module_filter_exit(void)
{
#ifdef __aarch64__
    ksu_unregister_syscall_hook(__NR_init_module);
    pr_info("init_module_filter: unregistered\n");
#endif
}
