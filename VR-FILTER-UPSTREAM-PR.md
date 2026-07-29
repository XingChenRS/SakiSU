# kernel: add init_module filter to block the vendor "vr" module

## What

Adds a self-contained kernel filter for affected vivo/iQOO GKI devices where
the vendor `vr.ko` module is loaded by vendor init and conflicts with
KernelSU.

The filter intercepts `init_module(2)` and `finit_module(2)`, parses the
module image's ELF `.modinfo`, and silently no-ops the load (returns `0`) only
when the declared module name is exactly `vr`. A parse failure, missing
section, or name mismatch falls through to the original syscall unchanged, so
unrelated module loading is not affected.

## Why use a direct syscall-table hook?

`vr.ko` is loaded by vendor init, which is not tracepoint-marked. A
tracepoint dispatcher hook therefore cannot observe this load. The filter
reuses the existing `ksu_syscall_table_hook()` infrastructure so it can
intercept the call from every process. The implementation is active on arm64
and is a no-op on other architectures.

## Scope

- New self-contained files: `kernel/hook/init_module_filter.{c,h}`.
- One object registration in `kernel/Kbuild`.
- An include plus init/exit calls in
  `kernel/hook/syscall_hook_manager.c`.
- No SakiSU branding, Manager changes, vermagic fallback, or other downstream
  behavior.

## Safety

- ELF metadata is read at explicit offsets with strict bounds on section
  count, section-name string table size, and `.modinfo` size. The complete
  module body is never copied into kernel memory.
- Both sources are covered: the `init_module` userspace buffer and the
  `finit_module` file descriptor.
- Every inspection failure fails open by calling the original syscall.
- Allocations and file references are released on all exit paths.

## Design question

The blocked module name is currently the literal `vr`. If a general
vendor-module blocklist is preferred, the name comparison is isolated and can
be made configurable without changing the ELF parser or hook lifecycle.

## Validation

The same filter is present in the final downstream SakiSU implementation.
Please use this PR's arm64 build checks as the authoritative remote compile
validation. On an affected vivo/iQOO device, the expected kernel log is:

```text
init_module_filter: hooked init_module + finit_module
init_module_filter: blocked vr (init_module)
```

The second line may report `finit_module` instead, depending on the vendor
loader.
