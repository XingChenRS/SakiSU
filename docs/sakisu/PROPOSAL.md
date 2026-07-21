# PROPOSAL — 转向重构提案 v1

> 状态：**v1 — 已纳入维护者决策**（2026-04-29）
> 基线：[VISION.md](VISION.md)
> 替换：v0 草案（已清理）；v0 §6 的 3 个待回答问题已在本版 §6 落地

## 1. 重构起点（已定）

**(a) 寄生在 ReSukiSU 架构上 + fork-and-inject 路线**：

- 维护者会再拉一份 ReSukiSU 源码作为基线；
- 本目录 6 份文档作为 `docs/sakisu/` 注入新仓；
- **暂不**重写 kernel / manager / ksud 主体，先做"轻量寄生"；
- 后期再视需要做**选择性重构**（具体范围由后续提案 v2+ 定义）。

> 理由：保留上游持续合流能力；6 份文档与 `platform_probe` 等已落地资产体量轻，注入成本低。

## 2. 舍弃项（不再投入）

| 舍弃 | 原因 |
|---|---|
| 多后端"统一能力契约"控制平面 | 维护成本与产品价值不匹配 |
| 六层架构 / 后端适配矩阵叙事 | 同上 |
| MagiskSU + KernelSU 双兼容管理器 | magiskd 不向第三方 manager 授权；fork 成本高 |
| Magisk 深度合并（接管 magiskd / 模块系统） | 不在 sakisu 范围 |
| **Magisk 主体借鉴**（架构 / 模块系统 / 守护进程设计） | 思路不契合内核优先方向 |
| **APatch 的 KernelPatch 路线**（外挂 payload 到 kernel 外 + 运行时 hook） | 与 sakisu 思路根本不同（见 §4 注释） |
| 常驻用户态 RPC / Socket | 抵消内核 root 隐蔽性优势（[VISION.md](VISION.md) §2.1） |
| `ksud sakisu run` 决策树编排执行 | 旧骨架未完成且与新方向不匹配 |
| `magisk_compat` 持续扩张 | 仅作基线探测保留（如保留），不再演进 |

## 3. 继承项（带入新仓）

| 继承 | 价值 |
|---|---|
| **隐蔽性硬约束**（[VISION.md](VISION.md) §2.1） | 内核 root 核心优势 |
| **保守拒绝二次 patch + `--force`** | 防 bootloop 的低成本兜底 |
| **手动回退兜底**（`F_RECOVERY_MANUAL_REQUIRED`） | 内核失权后的现实约束 |
| **失败分类错误码**（[DECISION-TREE.md](DECISION-TREE.md) §4） | 排错与可观测性基础 |
| **代码放置原则**（[PRINCIPLES.md](PRINCIPLES.md) §1） | 通用工程纪律 |
| **第三方源码引入约束**（[PRINCIPLES.md](PRINCIPLES.md) §5） | 防 LICENSE / 维护噩梦 |
| **`platform_probe` 实际探测代码** | 旧仓唯一已落地可用的 sakisu 资产 — 从 git 历史恢复（旧路径 `userspace/ksud/src/android/sakisu/platform_probe.rs`） |
| **`boot_patch.rs` 公共化辅助**（`inspect_boot_image_markers` / `load_ramdisk_cpio`） | 可向 ReSukiSU 上游回流 |

## 4. 汲取点（外部方案的具体长处）

> 仅作**线索**，不抬成同权规格（[VISION.md](VISION.md) §3）。

| 来源 | 可汲取 | 注意 |
|---|---|---|
| **KernelSU 主干** | LSM hook 框架、manager 鉴权、APK 签名校验 | 已是基线（寄生其上） |
| **SKRoot Lite** | 极简提权思路：把 uid0 直接交给目标进程 | **不具备** hook / 挂载 / 主动授权能力，只是参考其"最小提权通道"的设计简洁性 |
| **SKRoot Pro / 其 kernelpatch 工具** | 在**内核已有部分**打通一条定制提权通道（与外挂 payload 完全不同的思路） | **目前未开源**，等开源后再评估；本提案先把"原理可行性"作为未来方向预留，不做具体设计 |
| **kernel patching base 自研** | 为引入 LKM / inline hook 之外的第三种修补办法做技术储备 | 实现需严格区别于 APatch / KernelPatch 路线（见下方注释） |
| **Magisk 仅借鉴 `magiskboot`** | boot/init_boot/vendor_boot 镜像解析与修补工具链（业界事实标准） | **仅工具层借鉴**；Magisk 的架构 / 模块系统 / daemon 设计**不参考** |

### 4.1 重要区分：sakisu 内核 patch ≠ APatch 的 KernelPatch

| 维度 | APatch / KernelPatch | sakisu 目标方向（参考 SKRoot Pro 思路） |
|---|---|---|
| Payload 位置 | **外挂**一坨 payload 到 kernel 外的内存区域 | **改造**内核已有代码 / 数据通路 |
| Hook 时机 | 运行时 hook | 在内核已有的提权 / 权限检查路径上**接通定制通道** |
| 可检测面 | 较高（外挂内存可被识别） | 期望更低（与内核原生路径融合） |
| 开发依赖 | KernelPatch 工具链已开源 | SKRoot Pro 工具链未开源；先做技术储备 |

> **未开源前不动手**。把"内核内嵌式提权通道"作为长期方向放进路线图，避免被 APatch 路线带偏。

## 5. 启动清单（重构第 1 周）

- [x] 维护者裁定 v0 提案，决策合并入本 v1
- [x] 拉取 ReSukiSU 源码作基线（维护者负责）— 即仓库根目录
- [x] 锁定 ReSukiSU 上游基线 commit，写入 `UPSTREAM.md`（基线 `e8f607a2`，见仓库根）
- [x] 把本目录 6 份文档注入新仓（`docs/sakisu/`，2026-07-17）
- [ ] 从清空前的 git 历史恢复 `platform_probe.rs` 作为继承项种子（旧路径：`userspace/ksud/src/android/sakisu/platform_probe.rs`）
- [ ] 复刻 `.github/copilot-instructions.md`（清空前已建好的协作规则）
- [ ] 在 issue 区开一条 "watch SKRoot Pro open-source" 跟踪项
- [x] 评估 `magiskboot` 借鉴方式：走 ksud 内嵌 boot patch（标准 `patch`）；本地 `mk/` 保留预编译 magiskboot 作手动兜底，暂不摘代码到 `third_party/`。vivo 适配已改为运行时方案（ksuinit vermagic fallback + 内核 vr 拦截），不再走冷移除 `patch_vivo`。

## 6. v0 待回答问题 → v1 决策

| v0 §6 问题 | v1 决策 |
|---|---|
| 是否保留 `manager/` 应用代码？ | **保留**。寄生路线下 manager 跟随 ReSukiSU；选择性重构在 v2+ 评估 |
| "汲取 SKRoot / KernelPatch 长处" 优先级排序？ | **重排**：SKRoot Lite 思路 ＞ SKRoot Pro（待开源）；**APatch 的 KernelPatch 不汲取**（路线根本不同） |
| 是否保留 `magisk_compat` 轻量探测？ | **倾向保留**作"用户已有 Magisk"基线信号；不再演进；具体存废由代码 review 时定 |

## 7. v1 后新出现的待回答问题

- 自研 kernel patching base 的优先级如何排？是否要在 SKRoot Pro 开源前预启动技术调研（如阅读 KernelSU LSM hook 路径、SELinux avc 决策点）？
- `magiskboot` 的引入方式（依赖 / 摘代码 / 复刻）— 决定 LICENSE 与维护成本。
- 寄生期内 sakisu 自有改动落在哪里？建议 `userspace/ksud/src/sakisu/` + `kernel/sakisu/` 两个独立子树，避免污染上游同位文件。
