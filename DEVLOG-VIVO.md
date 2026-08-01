# DEVLOG: vivo / iQOO compatibility

This document tracks the implementation currently maintained by SakiSU. User-facing instructions live in `docs/vivo.md` and `docs/zh/vivo.md`.

## Current architecture

SakiSU uses three complementary mechanisms:

1. `userspace/ksuinit/src/lib.rs` keeps the runtime vermagic fallback for the standard LKM path.
2. `userspace/ksud/src/boot_patch.rs` provides strict vendor_boot rmvr for prebuilt `vr.ko` and `vklp.ko`.
3. `kernel/hook/init_module_filter.c` keeps the exact runtime `vr` load filter as a fallback.

No `_vivo` artifact or build-time hard-coded vermagic is produced.

## Runtime vermagic fallback

`ksuinit` opens kmsg before the first `init_module` attempt. If the load fails with a recognizable version-magic mismatch, it extracts the kernel-required value, appends a corrected `.modinfo` payload to the in-memory ELF module, updates the section header, and retries. Non-vermagic failures remain failures. This lets one standard KMI LKM adapt to device-specific release strings.

## vendor_boot rmvr

Manager classification calls `ksud boot-info classify-image`, which trusts only the parsed boot header. A selected vendor_boot image/partition is routed to `ksud boot-patch-rmvr`; boot and init_boot keep the standard `boot-patch` command.

The rmvr command:

- accepts only `BootImageVersion::Vendor(_)`;
- avoids GKI/KMI probing and never loads LKM assets;
- handles a single ramdisk and every entry in a v4 vendor ramdisk table;
- removes exact module basenames `vr.ko` and `vklp.ko`, including supported compressed suffixes;
- edits supported text indexes with token-aware matching, preserving similarly named modules;
- replaces only changed fragments;
- treats a no-match image as a byte-stable no-op and skips direct flashing;
- fails closed on an unparseable fragment.

Unit tests cover exact removal, similar-name preservation, index cleanup, no-op byte stability, and the vendor header gate.

## Runtime filter fallback

The arm64 `init_module`/`finit_module` syscall-table filter reads bounded ELF metadata and returns success without loading a module only when `.modinfo` declares the exact name `vr`. Parse errors and non-matches call the original syscall unchanged. This remains defense in depth; rmvr is the cold-removal route when vendor preload timing makes runtime interception insufficient.

## CI boundary

`.github/workflows/ddk-lkm.yml` builds one standard LKM per KMI. The obsolete `inputs.vivo`/hard-coded vermagic block is intentionally absent. Manager main/tag builds use the production signing certificate and verify the final APK after repacking.

## Verification still required on devices

- vendor_boot v3 and v4, including multiple fragments containing targets in different entries;
- boot/init_boot LKM install followed by runtime vermagic retry;
- direct and file-based rmvr, including A/B inactive-slot operation;
- boot, recovery, and fastbootd recovery paths;
- production Manager authorization (`Natives.isManager=true`) with a matching SakiSU LKM.
