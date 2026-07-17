# VISION

> **本文件是 sakisu 当前**唯一**有效的愿景声明。**
> 历史多版本（含 "多后端统一 / 双兼容管理器"等已废弃叙事）一律以本文件覆盖，原稿已清理。

## 1. 产品方向

**以内核级 root 方案为单一形态深化。**

- **主线**：在 KernelSU / ReSukiSU 血统主干上深化内核态能力（hook、policy、manager、supercall）。
- **汲取**：有选择地吸收 SKRoot / KernelPatch / Magisk 的实现长处（思路 / patch 策略 / 稳定性经验），**不等于**再做一套大一统适配矩阵。
- **不做**：
  - 多后端融合 / "统一能力契约"控制平面
  - MagiskSU + KernelSU 双兼容管理器（无独立 Magisk 应用时 magiskd 未必向第三方 manager 授权，工程性价比低）
  - Magisk 深度合并（fork magiskd / 接管模块系统）
  - 常驻用户态 RPC / Socket（见 §2 硬约束）

## 2. 硬约束

### 2.1 隐蔽性优先（最重要）

内核级 root 的**核心优势**是用户态特征暴露极少。任何设计都必须守住：

- **默认不引入常驻用户态 RPC 服务**（无长期监听 socket / 固定路径 / 稳定进程特征）。
- Manager↔后端优先走**最小暴露通道**：内核 UAPI（ioctl/JNI）+ 按需短生命周期执行。
- 新增对外接口必须评估：是否新增固定文件路径、常驻进程、可枚举端口/句柄、明显日志特征。

### 2.2 不假设可自动回退

内核级持久化失败可能导致 manager 失权，用户态无法再触发刷写。

- 默认兜底是**手动刷回**指引（`F_RECOVERY_MANUAL_REQUIRED`）。
- 仅当确认仍有恢复通道（如 uid==0 / adb root / fastbootd）时才尝试自动回退。

### 2.3 保守拒绝二次 patch

多个 patcher 可能修改重叠的 init/ramdisk/kernel 区域，二次 patch 容易产生 unbootable 镜像。

- patch 类操作前必须检测镜像洁净性。
- 检测不确定 → **默认拒绝**；用户可通过显式 `--force` 自负风险继续。

## 3. 厂商对抗（仅作线索，不入主仓规格）

- 某些厂商内核机制下 KernelPatch / APatch 易直接 bootloop，而 KSU / SKRoot 路径已能处理。
- vivo 系：`vermagic` 校验、`vr` 模块禁载、官核 LKM 限制等。

**处理方式**：在 issue / 外部笔记中跟踪，**不**抬成与本仓架构同权的规格书。

## 4. 范围声明

- 本仓**不**承诺：跨设备厂商完全适配、Magisk 模块生态完全兼容、所有内核版本无缝支持。
- 本仓**承诺**：在支持范围内（GKI 2.0 + 选定非 GKI），方向收敛、可观测、可手动回退。
