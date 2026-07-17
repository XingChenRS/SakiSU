# SakiSU

<img align="right" src="SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

[简体中文](zh/README.md) | **English** | [vivo/iQOO guide](vivo.md)

SakiSU is a downstream fork based on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU). It keeps the KernelSU/SukiSU style root manager, module system, App Profile, and related upstream work, while adding SakiSU-specific support for vivo/iQOO devices.

The current sync model is intentionally conservative: start from the latest ReSukiSU `main`, then replay SakiSU changes by topic.

## Highlights

- Kernel-level `su` and root authorization management.
- Module system, App Profile, and upstream ReSukiSU/SukiSU features.
- vivo/iQOO compatibility mode with the manager switch text `去除vr或适配vivo特性`.
- `vendor_boot` vivo path removes `vr.ko` only and skips KernelSU LKM injection.
- `init_boot` or compatible boot ramdisk path keeps normal LKM injection and prefers `_vivo` KMI/LKM variants.
- CI builds use a long-lived signing keystore when configured, with an ephemeral same-batch fallback for test builds.

## vivo/iQOO Behavior

When vivo mode is enabled, Manager invokes:

```text
ksud boot-patch-vivo
```

`ksud` then classifies the selected image from its actual ramdisk content:

| Image | Behavior |
|---|---|
| `vendor_boot.img` | Remove `vr.ko` and clean `modules.load`, `modules.dep`, `modules.softdep`, and `modules.load.recovery`; do not inject KernelSU LKM. |
| `init_boot.img` or boot ramdisk | Inject KernelSU LKM normally and prefer the matching `_vivo` KMI. |

The two operations are independent. Removing `vr.ko` from `vendor_boot` does not install KernelSU by itself, and patching `init_boot` does not automatically modify `vendor_boot`.

See [vivo/iQOO compatibility guide](vivo.md) for background, risks, and step-by-step usage.

## Documentation

- [Chinese documentation](zh/README.md)
- [vivo/iQOO compatibility guide](vivo.md)
- [vivo implementation notes](../DEVLOG-VIVO.md)
- [Upstream sync notes](../SAKISU-UPSTREAM-SYNC.md)
- [Upstream baseline lock](../UPSTREAM.md)
- [SakiSU vision / principles / proposal](sakisu/README.md)

## Credits

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU): current upstream base.
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): upstream lineage.
- [KernelSU](https://github.com/tiann/KernelSU): kernel-assisted root foundation.

## License

Files under `kernel` follow GPL-2.0-only. Other parts follow the license declarations in this repository.
