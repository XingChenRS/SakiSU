# SakiSU

<img align="right" src="SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

[简体中文](zh/README.md) | **English** | [vivo/iQOO guide](vivo.md)

SakiSU is an independently maintained downstream fork of [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU). It retains the KernelSU/SukiSU root manager, module system, App Profile, and related upstream work while maintaining its own compatibility, signing, and release policy.

## Maintenance status

**Active maintenance resumed on August 1, 2026.** SakiSU credits and audits upstream work but does not automatically mirror ReSukiSU. Changes are selected and reviewed for this downstream, which may intentionally use a different implementation.

The current compatibility model has two separate paths:

| Input | Behavior |
|---|---|
| `boot` / `init_boot` | Standard LKM patching. `ksuinit` keeps the runtime vermagic fallback and retries `init_module` with an in-memory `.modinfo` adjustment when required. |
| `vendor_boot` | Header-gated rmvr. Every vendor ramdisk fragment is inspected and existing `vr.ko` / `vklp.ko` files plus exact text-index references are removed. No KernelSU LKM or KSU/ADB configuration is injected. |

The Manager detects the path automatically from the selected partition or image header. SakiSU does not restore `_vivo` LKM artifacts or build-time hard-coded vermagic.

## Production signing

`kernel/manager/manager_sign.h` is the single source of truth for the official SakiSU Manager certificate. Gradle generates the Manager self-check values from that header. Main and tag workflows fail closed without the matching long-lived keystore, then verify the final repacked APK certificate again.

The historical `v4.2.0-sakisu.1` APK is correctly production-signed, but that tag still contained an old Manager UI constant and could falsely label itself unofficial. A later mainline fix corrected the constant; the new single-source build rule prevents this split from recurring.

Older ephemeral CI certificates cannot be upgraded in place to the production certificate, and an already-running old kernel cannot learn the new certificate automatically. Migrate by configuring Dynamic Manager with the old authorized Manager first, or by installing a new SakiSU kernel/LKM that trusts the production certificate before installing the production Manager.

## Safety notes

Modifying vendor_boot is device-sensitive. Keep a stock image and verify the partition and slot before flashing. If neither target module nor a matching index reference exists, rmvr reports a no-op and preserves the original image bytes.

## Documentation

- [Chinese documentation](zh/README.md)
- [vivo/iQOO compatibility guide](vivo.md)
- [SakiSU vision and engineering principles](sakisu/README.md)
- [Archived upstream sync notes](archive/SAKISU-UPSTREAM-SYNC.md)
- [Archived upstream baseline](archive/UPSTREAM-BASELINE.md)
- [Historical wind-down handoff](../HANDOFF.md)

## Credits

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU): current upstream source.
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): upstream lineage and compatibility work.
- [KernelSU](https://github.com/tiann/KernelSU): kernel-assisted root foundation.

## License

Files under `kernel` follow GPL-2.0-only. Other parts follow the license declarations in this repository.
