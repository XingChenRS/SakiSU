# vivo/iQOO Compatibility Guide

[简体中文](zh/vivo.md) | [Back to docs](README.md)

This page explains vivo/iQOO root restrictions in the GKI era and SakiSU's current **fully automatic** vivo solution, plus how to use it.

> **In one line**: vivo/iQOO support is now fully automatic. Patch `init_boot.img` with Manager (pick any standard KMI, no `_vivo` suffix), flash it, done. No switch or special step is required.

## Read This First

- Unlocking the bootloader and flashing `boot` / `init_boot` can brick the device or wipe data. Back up the original partitions before patching.
- Do not mix images from different firmware versions.
- A bad image can affect normal boot, recovery, and fastbootd. Keep the original image ready and reflash it if needed.
- Early 3.x/4.x vivo anti-root implementations embedded in vendor kernels require kernel reversing and are outside SakiSU's current scope.

## Background: vivo root Restrictions

Early vivo/iQOO anti-root logic was embedded in the vendor kernel, which was hard to deal with and usually required kernel reversing. After the GKI standard arrived, vendor-private drivers no longer belong in the generic kernel, so vivo moved this capability into vendor modules inside `vendor_boot` — the key one being the anti-root module `vr.ko`.

`vendor_boot` provides vendor drivers, mount config, and init scripts to the generic kernel as a ramdisk. At boot, vendor init loads these modules per its manifest. Once loaded, `vr.ko` hides itself, so it usually cannot be seen in the loaded-module list after boot.

## SakiSU's Fully Automatic Solution

SakiSU replaces the old "cold-edit `vendor_boot`" approach with two runtime mechanisms. Both are handled automatically by the built-in KernelSU LKM — **no switch and no manual step are required**.

### Runtime vermagic Adaptation

On a vivo official kernel, a plain GKI module fails to load because of a `vermagic` mismatch. The official kernel checks the `vermagic=` field in the module's ELF `.modinfo` against its own embedded expected string and rejects the module if they differ. A sample expected string looks like:

```text
6.1.145-android14-11-maybe-dirty SMP preempt mod_unload modversions vivo aarch64
```

The `vivo` marker in it is what makes a plain GKI LKM unacceptable to the official kernel.

SakiSU no longer ships a `_vivo`-suffixed variant per kernel at build time. Instead it adapts **at runtime**: when `ksuinit`'s first `init_module` call fails to load the KernelSU LKM, it reads the kernel log (`/dev/kmsg`), extracts the version magic the kernel requires, patches the in-memory module's `.modinfo`, and retries the load.

As a result, **a single generic LKM works for every KMI**. Users **do not need to hand-pick a `_vivo` KMI** — just pick the standard KMI matching your kernel version.

### Kernel `vr.ko` Interception

The SakiSU kernel hooks the arm64 `init_module` and `finit_module` syscalls. For each module-load request it parses the ELF `.modinfo`, and when the module's internal name is exactly `vr` it returns success to the caller (pretending the module loaded) **without actually loading it**. Vendor init believes `vr.ko` loaded, but it is not present in the kernel, and the anti-root module is silently blocked.

This requires that the **KernelSU LKM loads before `vr.ko`**. SakiSU's KernelSU is injected via `init_boot` and is ready before vendor modules load, so the syscall hook is in place when the `vr.ko` load request arrives.

Because interception happens at runtime, SakiSU **no longer cold-removes `vr.ko` from `vendor_boot`** and **does not modify the `vendor_boot` partition** at all. You only need to touch `init_boot`.

## Manager Workflow

### Prepare

1. Confirm the bootloader is unlocked.
2. Get an `init_boot.img` matching your current firmware version (a few older devices use `boot.img`).
3. Back up the original `boot`, `init_boot`, `vbmeta`, etc.
4. Install SakiSU Manager.

### Patch and Flash `init_boot`

1. Open SakiSU Manager and go to the install page.
2. Choose file patching and select the `init_boot.img` for your current firmware. A few older devices may use `boot.img`.
3. If a KMI dialog appears, pick the **standard KMI matching your kernel version — no `_vivo` suffix needed**.
4. Wait for the patched image output.
5. Reboot to the bootloader and flash the output image back to the corresponding partition:

```text
fastboot flash init_boot kernelsu_patched_xxx.img
fastboot reboot
```

Use `boot` instead of `init_boot` only on devices that actually use the `boot` partition. Flashing an image from the wrong firmware version may not boot.

That's it. There is no separate `vendor_boot` step and no more "two-step flashing". After boot, vermagic adaptation and `vr.ko` interception take effect automatically.

## Manual Study of `init_boot` (Optional)

If you want to understand the patch flow by hand, you can use the official Linux `magiskboot`. The Android Magisk App bundles `libmagiskboot.so`, which is the magiskboot implementation usable in Linux/Android environments.

Basic flow:

```text
./magiskboot unpack init_boot.img
# inspect / replace init resources in the ramdisk
./magiskboot repack init_boot.img
```

`vr.ko` is handled entirely at runtime by the kernel, so **no cold removal in any image is needed**. The manual flow is only suggested for verification and research; normal users should use SakiSU Manager.

## FAQ

### Do I need to remove `vr.ko` manually?

No. The SakiSU kernel intercepts `vr.ko` load requests at runtime and does not touch the `vendor_boot` partition.

### Do I need to pick a `_vivo` KMI?

No. Runtime vermagic adaptation lets a single generic LKM work for every KMI. Just pick the standard KMI matching your kernel version.

### The official kernel fails to load the KernelSU LKM

First confirm the flashed image matches your current firmware version. Normally `ksuinit` reads the required vermagic from the kernel log after the first failure and retries automatically, so no manual action is usually needed.

### It doesn't boot after flashing

Reflash the original image to recover. Common causes include version mismatch, flashing the wrong partition, or extra boot-chain checks on the device itself.

### Can APatch, SKRoot, etc. coexist?

SakiSU only blocks `vr.ko` from loading at runtime and does not actively prevent you from exploring kernel-level solutions like APatch or SKRoot, but each device and kernel version still needs separate verification.
