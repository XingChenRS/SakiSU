# SakiSU

<img align="right" src="SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

[简体中文](zh/README.md) | **English** | [vivo/iQOO guide](vivo.md)

SakiSU is a downstream fork based on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU). It keeps the KernelSU/SukiSU style root manager, module system, App Profile, and related upstream work, while adding SakiSU-specific support for vivo/iQOO devices.

**SakiSU stopped active development on 2026-07-26.** The final release is
`v4.2.0-sakisu.1`. The repository no longer follows upstream or starts builds
from ordinary `main` pushes. The runtime vermagic fallback now exists
upstream, and the `vr.ko` filter is being handed back as a focused upstream
patch. See the [wind-down handoff](../HANDOFF.md).

## Highlights

- Kernel-level `su` and root authorization management.
- Module system, App Profile, and upstream ReSukiSU/SukiSU features.
- **Automatic vivo/iQOO compatibility**: runtime vermagic adaptation plus kernel-level `vr.ko` blocking — no vendor_boot modification, no `_vivo` LKM variants.
- Build workflows remain available for deliberate manual validation but no longer run automatically on branch pushes.

## vivo/iQOO Behavior

Vivo/iQOO compatibility is fully automatic and requires no special switch:

| Mechanism | What it does |
|---|---|
| Runtime vermagic fallback (`ksuinit`) | On the first `init_module` failure, reads the kernel log, extracts the required version magic, patches the module's in-memory `.modinfo`, and retries. A single universal LKM works across every KMI. |
| Kernel `vr.ko` blocking (`init_module_filter`) | Hooks arm64 `init_module`/`finit_module` and returns success for the exact module named `vr` without loading it, so vendor anti-root never activates. No cold removal from `vendor_boot`. |

Both run in the standard `boot-patch` flow. Just patch `init_boot.img` with any standard KMI and flash it.

See [vivo/iQOO compatibility guide](vivo.md) for background, risks, and step-by-step usage.

## Documentation

- [Chinese documentation](zh/README.md)
- [vivo/iQOO compatibility guide](vivo.md)
- [vivo implementation notes](../DEVLOG-VIVO.md)
- [Archived upstream sync notes](../SAKISU-UPSTREAM-SYNC.md)
- [Upstream baseline lock](../UPSTREAM.md)
- [SakiSU vision / principles / proposal](sakisu/README.md)

## Credits

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU): current upstream base.
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): upstream lineage.
- [KernelSU](https://github.com/tiann/KernelSU): kernel-assisted root foundation.

## License

Files under `kernel` follow GPL-2.0-only. Other parts follow the license declarations in this repository.
