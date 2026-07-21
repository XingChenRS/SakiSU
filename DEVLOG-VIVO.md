# DEVLOG: vivo / iQOO compatibility

Vivo/iQOO compatibility is **fully automatic and runtime-based**. There is
no manager switch, no `_vivo` LKM variant, and no `vendor_boot` cold
removal. This file records what the code actually does; keep it aligned with:

- `userspace/ksuinit/src/lib.rs` — runtime vermagic fallback
- `kernel/hook/init_module_filter.c` — `vr.ko` blocking
- `kernel/hook/syscall_hook_manager.c` — hook registration/teardown

User-facing usage lives in `docs/zh/vivo.md` and `docs/vivo.md`.

## Problem

Vivo GKI devices enforce anti-root two ways:

1. The stock kernel validates a module's `vermagic`; a generic GKI LKM whose
   version magic lacks the device-specific string (e.g. `... vivo aarch64`)
   is rejected.
2. `vendor_boot` ships `vr.ko`, a vendor anti-root module that hides itself
   after loading and enforces restrictions.

## Mechanism 1 — Runtime vermagic fallback (ksuinit)

`load_module()` in `userspace/ksuinit/src/lib.rs`:

1. Before the first `init_module`, open `/dev/kmsg` (fallback `/kmsg`) with
   `O_NONBLOCK` and seek to the end.
2. First `init_module` success returns immediately — no modification.
3. On the first failure, drain the newly produced log records and only
   proceed if a line matches `version magic '...' should be '<kernel>'`.
4. Extract the kernel-required vermagic (bounded parse of the kmsg record,
   stripping the `<prio>,<seq>,<time>,<flags>;` prefix).
5. Parse the ELF64 module, append a fresh `.modinfo` (with the new
   `vermagic=`) to the end of the buffer, and repoint the section header
   (`sh_offset` +0x18, `sh_size` +0x20). All other sections keep their
   original file offsets, so the module stays loadable.
6. Retry `init_module` with the patched buffer.
7. Non-vermagic errors, a missing `.modinfo`, or an unrecognized log format
   fall through to the original error — no blind retry.

A single universal LKM adapts to every KMI. Aligned with upstream
ReSukiSU@83d1806.

## Mechanism 2 — Kernel `vr.ko` blocking (init_module_filter)

`kernel/hook/init_module_filter.c` registers, at LKM init, **direct
syscall-table hooks** (`ksu_syscall_table_hook`, not the tracepoint
dispatcher) for arm64 `__NR_init_module` and `__NR_finit_module`:

- Read the ELF header, section table, shstrtab, and `.modinfo` at explicit
  offsets — from the user buffer (`init_module`) or the fd via
  `ksu_kernel_read_compat` (`finit_module`). The full module body is never
  copied, so module size is irrelevant.
- Extract the `.modinfo` `name=` value. If it is exactly `vr`, return 0
  (fake success) without loading the module.
- Any parse failure, name mismatch, out-of-bounds offset, or allocation
  failure falls through to the original syscall (fail-open).

Because the syscall table is patched directly, the hook fires for **every
process** — including vendor init, which is never tracepoint-marked. This is
why the dispatcher API could not be used here.

**Prerequisite:** the KernelSU LKM (injected via `init_boot`) must load
before `vr.ko`. In the normal boot order the `init_boot` first-stage init
runs `ksuinit` before vendor modules are loaded, so the hook is in place in
time.

Registration is in `ksu_syscall_hook_manager_init()`; teardown (unhook both
syscalls) is at the start of `ksu_syscall_hook_manager_exit()`, before the
syscall table is restored.

## User Flow

```text
init_boot.img:
  Install -> SelectFile -> choose init_boot.img
  -> choose any standard KMI (no _vivo suffix)
  -> standard boot-patch injects KernelSU LKM + ksuinit
  -> on boot: ksuinit auto-adapts vermagic, kernel auto-blocks vr.ko
```

No separate `vendor_boot` step. No KMI `_vivo` selection. No manager switch.

## Signing State

Unrelated to vivo, but the manager APK trust path was hardened alongside
(see CVE-2023-46139 / GHSA-86cp-3prf-pwqq):

- `kernel/manager/apk_sign.c` and `userspace/ksud/src/apk_sign.rs` reject
  duplicate v2 signature blocks, reject v1-only / v1-downgrade, and
  cross-verify v3/v3.1 certificates against the trust list.
- CI prefers repository `KEYSTORE` secrets; `dev`/test branches may use an
  ephemeral same-batch key; only `main` enforces production signing.

## Verification

After a normal boot (with root) the kernel log should show:

```text
init_module_filter: hooked init_module + finit_module
init_module_filter: blocked vr (init_module)     # or (finit_module)
Replaced module vermagic with kernel-required value: "..."   # if needed
```

## Known Limitations

1. **Load order**: if a device loads `vr.ko` before the KernelSU LKM, the
   hook cannot intercept it.
2. **arm64 only**: `init_module_filter` hooks arm64; other arches are not
   covered.
3. **Forced module signing**: on kernels with `CONFIG_MODULE_SIG_FORCE=y`,
   userspace module rewriting may affect signature verification — verify per
   device.
4. **Name dependency**: blocking keys on `.modinfo` `name=vr`; a renamed or
   packed module would need separate handling.

## Removed (archived)

Fully removed from SakiSU; kept here only to prevent regressions:

- **Build-time `_vivo` LKM**: `ddk-lkm.yml` `vivo` input + hardcoded/templated
  vermagic, `<kmi>_vivo_kernelsu.ko`, and `build-lkm-vivo.yml`. Replaced by
  Mechanism 1.
- **`vendor_boot` cold removal (rmvr)**: `ksud boot-patch-vivo` /
  `patch_vivo()` / `remove_vendor_modules()`, `boot-info classify-image`,
  the manager `去除vr或适配vivo特性` switch, and `_vivo` KMI selection.
  Replaced by Mechanism 2.

See commits `cleanup: remove build-time vivo vermagic injection` and
`cleanup: remove legacy vivo/rmvr frontend`.
