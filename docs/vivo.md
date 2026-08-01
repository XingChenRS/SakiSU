# vivo/iQOO compatibility

SakiSU keeps runtime vermagic adaptation for the standard LKM path and restores a separate vendor_boot rmvr path for devices that preload conflicting vendor modules.

## Two independent paths

1. Patch `boot` or `init_boot` to install the standard `kernelsu.ko`. If the first `init_module` call reports a vermagic mismatch, `ksuinit` reads the required value from the kernel log, updates the module in memory, and retries. There is no `_vivo` LKM build.
2. Select the `vendor_boot` partition or import a vendor_boot image to run rmvr. SakiSU verifies the vendor boot header, traverses every ramdisk fragment, and removes existing `vr.ko` / `vklp.ko` files and exact references in supported text module indexes. This path never installs KernelSU or edits KSU/ADB ramdisk settings.

The existing exact runtime `vr` load filter remains a fallback in SakiSU. Cold removal is still useful when a preloaded module interferes before the runtime path is available.

## Recommended procedure

1. Back up stock boot, init_boot, and vendor_boot images for the active slot.
2. In SakiSU Manager, patch or install to the normal boot/init_boot partition first.
3. Return to Install, select `vendor_boot`, and run the operation again to perform rmvr. For file patching, select the vendor_boot image; the header is detected automatically.
4. Review the flash log. A no-match result is a successful no-op and is not flashed automatically.
5. Reboot only after both required operations have completed successfully.

## Risks and recovery

Vendor boot layouts vary by device. SakiSU supports a single ramdisk (vendor boot v3 and table-less layouts) and all fragments in a v4 ramdisk table, but a malformed or non-CPIO fragment causes a fail-closed error. Keep a stock image and a known recovery/fastbootd path. Never flash an output to a partition whose header was not recognized as vendor_boot.

## Signing migration

If an older Manager used an ephemeral CI certificate, configure Dynamic Manager while that Manager is still authorized, or install a new SakiSU kernel/LKM that trusts the production certificate before replacing the APK. Android and an already-running old kernel cannot automatically accept a different signing certificate.
