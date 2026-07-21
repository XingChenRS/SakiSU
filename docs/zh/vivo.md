# vivo/iQOO 适配教程

[English](../vivo.md) | [返回中文首页](README.md)

这篇文档介绍 vivo/iQOO 在 GKI 时代的 root 限制，以及 SakiSU 当前的**全自动** vivo 方案和使用方法。

> **一句话总结**：现在 vivo/iQOO 兼容是全自动的。用 Manager 修补 `init_boot.img`（选任意标准 KMI，无需 `_vivo` 后缀）、刷入，即可完成。不需要任何开关或特殊操作。

## 先读风险

- 解锁 bootloader、刷写 `boot`、`init_boot` 等分区都有变砖或丢数据风险。动手前先备份原始分区。
- 不要混用不同系统版本的镜像。
- 刷错镜像可能影响正常启动、recovery 和 fastbootd。能进 fastboot 时优先刷回原始镜像恢复。
- 3.x/4.x 时代 vivo 将反 root 逻辑嵌入内核，需要逆向内核处理，不属于 SakiSU 当前处理范围。

## vivo root 限制背景

早期 vivo/iQOO 的反 root 逻辑嵌入在厂商内核中，处理难度高，往往需要逆向内核。GKI 标准引入后，厂商私有驱动和功能不应继续塞进通用内核，vivo 将这部分能力迁移到 `vendor_boot` 里的厂商模块中，其中最关键的是反 root 模块 `vr.ko`。

`vendor_boot` 以 ramdisk 的形式向通用内核提供厂商驱动、挂载配置和初始化脚本。设备启动时，vendor init 会按清单加载这些模块。`vr.ko` 被加载后会隐藏自身，因此开机后通常无法直接从已加载模块列表中看到它。

## SakiSU 的全自动方案

SakiSU 用两项运行时机制彻底取代了以往「冷改 `vendor_boot`」的做法。它们都由内置的 KernelSU LKM 自动完成，用户**无需任何开关或手动步骤**。

### 运行时 vermagic 适配

在 vivo 官方内核上，普通 GKI 模块会因为 `vermagic` 不匹配而加载失败。vivo 官方内核会校验模块 ELF `.modinfo` 中的 `vermagic=`，与内核内嵌的期望串比较；不匹配则拒绝加载。样本中的期望串可能类似：

```text
6.1.145-android14-11-maybe-dirty SMP preempt mod_unload modversions vivo aarch64
```

其中的 `vivo` 字段会让普通 GKI LKM 无法被官方内核接受。

SakiSU 不再在构建期为每种内核准备带 `_vivo` 后缀的变体。取而代之的是**运行时自适应**：`ksuinit` 第一次调用 `init_module` 加载 KernelSU LKM 失败时，会读取内核日志（`/dev/kmsg`），从中提取内核要求的 version magic，直接修补内存中模块的 `.modinfo`，然后重试加载。

因此**单一通用 LKM 就能适配所有 KMI**，用户**不需要手选 `_vivo` KMI**，任选与本机内核版本匹配的标准 KMI 即可。

### 内核 `vr.ko` 拦截

SakiSU 内核 hook 了 arm64 的 `init_module` 和 `finit_module` 系统调用。当有模块加载请求时，它会解析 ELF 的 `.modinfo`，只要模块内部名称精确等于 `vr`，就直接向调用方返回成功（假装已加载）而**不真正加载**该模块。这样 vendor init 以为 `vr.ko` 加载成功，实际内核里没有它，反 root 模块被无声阻断。

这套机制的前提是 **KernelSU LKM 比 `vr.ko` 先加载**。SakiSU 的 KernelSU 由 `init_boot` 注入，在 vendor 模块加载前就已就绪，因此系统调用 hook 能在 `vr.ko` 的加载请求到达时拦截它。

正因为在运行时拦截，SakiSU**不再冷移除 `vendor_boot` 里的 `vr.ko`**，也**不修改 `vendor_boot` 分区**。你只需要处理 `init_boot`。

## Manager 使用教程

### 准备

1. 确认设备已解锁 bootloader。
2. 准备和当前系统版本一致的 `init_boot.img`（少数旧设备使用 `boot.img`）。
3. 备份原始 `boot`、`init_boot`、`vbmeta` 等分区。
4. 安装 SakiSU Manager。

### 修补并刷入 `init_boot`

1. 打开 SakiSU Manager，进入安装页。
2. 选择文件修补，选择当前系统对应的 `init_boot.img`。少数旧设备可能使用 `boot.img`。
3. 弹出 KMI 选择时，选择与本机内核版本匹配的**标准 KMI 即可，无需带 `_vivo` 后缀**。
4. 等待输出修补后的镜像。
5. 重启到 bootloader，将输出镜像刷回对应分区：

```text
fastboot flash init_boot kernelsu_patched_xxx.img
fastboot reboot
```

如果设备没有 `init_boot` 分区而使用 `boot` 分区，请刷入 `boot`。刷错系统版本的镜像可能无法启动。

到这一步就完成了。不需要单独处理 `vendor_boot`，也不再有「两步刷机」流程。开机后 vermagic 自适配和 `vr.ko` 拦截会自动生效。

## 手动研究 `init_boot`（可选）

如果你想手动理解修补流程，可以使用 Magisk 官方 Linux 版 `magiskboot`。Android 构建的 Magisk App 内含 `libmagiskboot.so`，它就是可用于 Linux/Android 环境的 magiskboot 实现。

基本流程：

```text
./magiskboot unpack init_boot.img
# 查看/替换 ramdisk 中的 init 资源
./magiskboot repack init_boot.img
```

`vr.ko` 的处理完全在运行时由内核完成，**无需在镜像里做任何冷移除**。手动流程只建议用于验证和研究，普通用户优先使用 SakiSU Manager。

## 常见问题

### 需要手动去除 `vr.ko` 吗

不需要。SakiSU 内核在运行时拦截 `vr.ko` 的加载请求，不改动 `vendor_boot` 分区。

### 需要选择 `_vivo` KMI 吗

不需要。运行时 vermagic 适配让单一通用 LKM 适配所有 KMI，任选与本机内核版本匹配的标准 KMI 即可。

### 官方内核无法加载 KernelSU LKM

先确认所刷镜像与当前系统版本一致。正常情况下 `ksuinit` 会在首次加载失败后从内核日志读取所需 vermagic 并自动重试，一般不需要人工干预。

### 刷入后不开机

先刷回原始镜像恢复。常见原因包括镜像版本不匹配、刷错分区，或设备本身的启动链路还有额外校验。

### APatch、SKRoot 等方案能否配合

SakiSU 只在运行时阻断 `vr.ko` 的加载，不会主动阻止你研究 APatch、SKRoot 等内核级方案，但不同设备和内核版本仍需要单独验证。
