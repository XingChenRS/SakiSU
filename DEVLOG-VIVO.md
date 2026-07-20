# DEVLOG: vivo / iQOO compatibility

This file records the evolution of vivo/iQOO compatibility implementation.

**Current approach (2026-07-21+):**
- Runtime vermagic fallback in ksuinit (commit 0c0867a0)
- Kernel init_module hook for vr.ko blocking (commit fc295db9)
- No build-time _vivo LKM variants
- No vendor_boot cold removal by default

**Legacy approach (deprecated 2026-07-21):**
- Build-time vermagic injection for _vivo KMI variants
- Cold removal of vr.ko from vendor_boot
- See git history before commit 042832a8 for implementation details

---

## Current Implementation

### Runtime vermagic fallback (ksuinit)

`userspace/ksuinit/src/lib.rs` implements automatic vermagic mismatch recovery:

1. First `init_module` attempt
2. On failure, open `/dev/kmsg` and seek to end
3. Parse new kernel messages for "version magic ... should be '<vermagic>'"
4. Extract required vermagic from kernel log
5. Parse ELF64 module, locate `.modinfo` section
6. Replace `vermagic=` entry with kernel-required value
7. Update ELF section header `sh_size`
8. Retry `init_module` with patched module

Safety boundaries:
- Only triggers on first failure with vermagic mismatch log
- Strict ELF64 validation (magic, type, bounds)
- Falls back to original error if parsing fails
- No disk modification, only in-memory patching

### Kernel vr.ko blocking (init_module filter)

`kernel/hook/init_module_filter.c` intercepts arm64 `__NR_init_module`:

1. Hook registered via `ksu_register_syscall_hook()`
2. On syscall entry, allocate kernel buffer (up to 64KB)
3. `copy_from_user()` module image from userspace
4. Parse ELF64 header + section table + `.modinfo`
5. Extract `name=` field from NUL-separated entries
6. If name exactly equals `"vr"`, return 0 (fake success)
7. Otherwise call original `ksu_syscall_table[__NR_init_module](regs)`

Safety boundaries:
- arm64-only (`#ifdef __aarch64__`)
- Strict bounds checking on all ELF offsets
- Any parse error → call original syscall
- Only precise string match triggers block
- `kmalloc()` failure → call original syscall

Integration:
- Registered in `ksu_syscall_hook_manager_init()`
- Unregistered in `ksu_syscall_hook_manager_exit()`
- Added to `kernel/Kbuild`

---

## Code Alignment

Keep aligned with:
- `userspace/ksuinit/src/lib.rs` - vermagic fallback
- `kernel/hook/init_module_filter.c` - vr blocking
- `kernel/hook/syscall_hook_manager.c` - hook lifecycle
- `.github/workflows/ddk-lkm.yml` - single universal LKM build

User-facing docs:
- `docs/zh/vivo.md` (Chinese)
- `docs/vivo.md` (English)

---

## Deprecated Implementation (archived)

### Build-time _vivo LKM (removed 2026-07-21)

**What it did:**
- `.github/workflows/build-lkm-vivo.yml` - matrix build for all KMI
- `.github/workflows/ddk-lkm.yml` - `vivo: true` input parameter
- Injected hardcoded vermagic strings for specific KMI versions
- Produced `<kmi>_vivo_kernelsu.ko` artifacts

**Why deprecated:**
- Runtime vermagic fallback is more robust
- No need to maintain KMI-specific templates
- Works across kernel updates without LKM rebuild
- Single universal LKM for all devices

**Removed in:** commit 042832a8

### Cold vr.ko removal (legacy fallback)

**What it did:**
- `ksud boot-patch-vivo` subcommand
- Detected `vendor_boot` by ramdisk content
- Removed `vr.ko` and `modules.*` references
- Did not inject KernelSU LKM into vendor_boot

**Status:**
- Command may still exist for compatibility
- Not the default vivo approach
- Kernel hook is preferred (no partition modification)

---

## Migration Notes

### For developers

Old code references to remove:
- `_vivo` KMI suffix in UI/CLI
- `boot-patch-vivo` as default path
- KMI selection prompts mentioning vivo variants
- Hardcoded vermagic templates

New behavior:
- All vivo devices use standard LKM
- ksuinit handles vermagic automatically
- Kernel blocks vr.ko transparently
- No user-visible vivo-specific artifacts

### For users

Old workflow (deprecated):
1. Enable vivo switch
2. Select `init_boot.img`
3. Manually choose `_vivo` KMI from dialog
4. Flash patched image

New workflow (current):
1. Enable vivo switch (optional, for future features)
2. Select `init_boot.img`
3. Use any standard KMI (no _vivo suffix)
4. Flash patched image
5. ksuinit auto-adapts vermagic on first boot
6. Kernel auto-blocks vr.ko if present

---

## Known Limitations

1. **init_module only**: Does not cover `finit_module` (Android init rarely uses it)
2. **arm64 only**: x86_64 and other arches not implemented
3. **Requires KSU LKM loads before vr.ko**: Init order dependency
4. **No vr.ko detection in finit_module**: If vivo changes loading method, hook needs update

## Testing Checklist

- [ ] Vivo device boots with standard (non-_vivo) LKM
- [ ] ksuinit retries on vermagic mismatch
- [ ] vr.ko load attempts are blocked (check dmesg)
- [ ] Other vendor modules load normally
- [ ] Manager shows KernelSU version correctly
- [ ] Root permissions work as expected

---

## References

- Upstream vermagic commit: ReSukiSU/ReSukiSU@83d1806
- CVE-2023-46139 fix: tiann/KernelSU@d24813b2
- SakiSU implementation: commits 0c0867a0, fc295db9, 042832a8

## Scope

The manager-side switch is intentionally simple: **`去除vr或适配vivo特性`**.

When enabled, SakiSU uses one backend command:

```text
ksud boot-patch-vivo
```

`ksud` decides what the selected image actually needs.

| Image | Action |
|---|---|
| `init_boot.img` or compatible boot ramdisk | Inject `kernelsu.ko` and `ksuinit`; prefer the `_vivo` KMI/LKM variant. |
| `vendor_boot.img` | Remove `vr.ko` and its `modules.*` references only; do not inject KernelSU files. |

Turning the vivo switch off restores the normal SakiSU patch flow.

## Backend Rules

`userspace/ksud/src/boot_patch.rs` keeps vivo handling partition-agnostic:

1. `patch_vivo()` adds `vr.ko` to `remove_module` if it is not already present.
2. `patch_vivo()` then calls the normal `patch()` path.
3. `patch()` loads the ramdisk cpio before loading embedded LKM resources.
4. If the cpio contains `lib/modules/*.ko`, the image is treated as `vendor_boot` and `no_install` is enabled automatically.
5. `remove_vendor_modules()` discovers `lib/modules` and `lib/modules/<version>-gki/` roots dynamically.
6. The cleanup covers `modules.load`, `modules.dep`, `modules.softdep`, and `modules.load.recovery`.

The backend also exposes:

```text
ksud boot-info classify-image <image>
```

It prints one of:

- `vendor_boot`
- `init_boot`
- `unknown`

Manager uses this classification to avoid unnecessary KMI dialogs without duplicating boot-image parser logic.

## Manager Flow

`KsuCli.kt` copies a selected image into cache and calls `boot-info classify-image` when classification is needed.

`Install.kt` uses the result like this:

- SelectFile + vivo ON + classified `vendor_boot`: skip the KMI dialog and run rmvr through `boot-patch-vivo`.
- SelectFile + vivo ON + classified `init_boot` or `unknown`: keep the KMI dialog, because LKM injection may be needed.
- DirectInstall + selected partition `vendor_boot`: skip the KMI dialog.
- Other GKI install paths with no custom `.ko`: keep the KMI dialog.

When vivo mode is enabled and a KMI string is selected, `installBoot()` appends `_vivo` unless the selected KMI already has that suffix. This is harmless for vendor_boot rmvr because the backend skips LKM resource loading on that path.

## Expected User Flow

```text
init_boot.img:
  Install -> SelectFile -> choose init_boot.img
  -> choose androidXX-Y.Z_vivo KMI
  -> boot-patch-vivo injects KernelSU LKM

vendor_boot.img:
  Install -> SelectFile -> choose vendor_boot.img
  -> no KMI dialog when classification succeeds
  -> boot-patch-vivo removes vr.ko and modules.* references only
```

## Signing State

The current signing path must remain compatible with modern Android Gradle Plugin output:

- `kernel/manager/apk_sign.c` requires a trusted v2 certificate.
- If v3 or v3.1 signature blocks are present, their certificates must also be trusted.
- `userspace/ksud/src/apk_sign.rs` no longer rejects APKs merely because v3/v3.1 exists.
- CI prefers repository `KEYSTORE` secrets; if they are absent, it generates an ephemeral same-batch key and passes its certificate size/hash to both LKM and Manager builds.

Do not reintroduce a blanket v2-only signing requirement unless the kernel verifier policy changes again.

## Lessons Kept

- Do not gate rmvr on `partition == "vendor_boot"`; SelectFile may not expose a partition dropdown.
- Do not make the vivo switch mean "rmvr only"; `init_boot` still needs LKM injection.
- Do not hard-code `lib/modules/6.1-gki`; vivo devices also ship 5.10, 5.15, 6.6, 6.12, and other layouts.
- Do not load embedded LKM assets before the vendor_boot decision, because vendor_boot rmvr should not need LKM assets at all.
- Keep the exact `base.apk` match in `kernel/manager/throne_tracker.c`; prefix matching accepts `base.apk.prof` or `base.apk.idsig` and breaks manager detection.
