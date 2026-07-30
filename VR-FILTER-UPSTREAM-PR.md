# kernel: add init_module filter to block the vendor "vr" module

## Summary

Add a self-contained arm64 kernel filter for affected vivo/iQOO GKI devices
where vendor init loads `vr.ko`, which conflicts with KernelSU.

The filter intercepts `init_module(2)` and `finit_module(2)`, reads the module
name from the ELF `.modinfo` section, and returns success without loading the
module only when the declared name is exactly `vr`.

## Problem

The vendor module is loaded by vendor init. That process is not marked for the
existing tracepoint dispatcher, so a dispatcher hook cannot observe this load.
The filter therefore reuses the existing `ksu_syscall_table_hook()`
infrastructure to cover calls from every process.

## Implementation

- Add `kernel/hook/init_module_filter.{c,h}`.
- Register one object in `kernel/Kbuild`.
- Initialize and release the filter from
  `kernel/hook/syscall_hook_manager.c`.
- Inspect both the `init_module` userspace buffer and the `finit_module` file
  descriptor.
- Keep non-arm64 builds as no-ops.

## Safety

- ELF metadata is read at explicit offsets with strict bounds on the section
  count, section-name string table, and `.modinfo` size. The complete module
  body is never copied into kernel memory.
- A parse failure, missing section, or name mismatch fails open by calling the
  original syscall unchanged.
- Allocations and file references are released on all exit paths.
- The comparison is limited to the exact module name `vr`; unrelated modules
  are not blocked.

## Testing

Device test:

- vivo PD2324M (`V2324HA`)
- Android 16 (SDK 36)
- Kernel `6.1.145-android14-11-maybe-dirty`
- The downstream build containing the same filter was installed and the
  affected-device test completed successfully.

Build and static validation:

- The downstream Android 12-16 GKI/LKM matrix and Manager build passed in
  [GitHub Actions](https://github.com/XingChenRS/SakiSU/actions/runs/30466010219).
- `clang-format --dry-run --Werror` passes for the new C and header files.
- `git diff --check` passes against current ReSukiSU `main`.

Upstream CI should be treated as the final compile validation after this
branch is pushed and the PR is opened.

## Scope

This change contains no SakiSU branding, Manager changes, vermagic fallback,
or unrelated downstream behavior.

The blocked module name is intentionally a literal `vr`. If upstream prefers a
general vendor-module blocklist, the comparison is isolated and can be made
configurable without changing the ELF parser or hook lifecycle.
