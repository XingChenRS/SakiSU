#ifndef __KSU_H_INIT_MODULE_FILTER
#define __KSU_H_INIT_MODULE_FILTER

#include <asm/ptrace.h>

// Register init_module filter for vr.ko blocking.
// Must be called after syscall_hook_init.
void ksu_init_module_filter_init(void);

// Unregister and clean up.
void ksu_init_module_filter_exit(void);

#endif
