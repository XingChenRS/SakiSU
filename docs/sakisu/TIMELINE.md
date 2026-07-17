# TIMELINE — 方向反复浓缩

> 旧 `devlog.md`（19KB / 326 行 / 14 条记录）的浓缩版（原稿已清理）。
> 目的：让重构者用 1 分钟看到**为什么是现在的方向**，避免再走一遍弯路。

## 时间线（2026-04-06 → 2026-04-07，两天）

| # | 时刻 | 关键动作 | 状态 |
|---|---|---|---|
| 1 | 04-06 18:35 | 落地"统一架构"6 份英文文档（六层架构、capability-uapi、backend-matrix、migration-roadmap） | ❌ **后被推翻**（§2 § 5） |
| 2 | 04-06 19:10 | 新增 4 阶段决策树文档；建 `userspace/ksud/src/android/sakisu/` 骨架 | ✅ 决策树骨架被继承（[DECISION-TREE.md](DECISION-TREE.md)） |
| 3 | 04-06 19:40 | `ksud sakisu probe/run/checkpoints` 子命令；`platform_probe` 真实探测落地 | ✅ `platform_probe` 是唯一可用代码资产 |
| 4 | 04-06 20:05 | **硬约束**：默认不引入常驻用户态 RPC（避免暴露面） | ✅ 继承（[VISION.md](VISION.md) §2.1） |
| 5 | 04-06 20:30 | 加 `--json` 输出，为 GUI"按需一次性调用"铺路 | — |
| 6 | 04-06 20:45 | **修正 Stage D**：root 失权后通常不可自动回退，输出 `F_RECOVERY_MANUAL_REQUIRED` | ✅ 继承（[VISION.md](VISION.md) §2.2） |
| 7 | 04-06 21:10 | **保守拒绝二次 patch**，需显式 `--force` | ✅ 继承（[VISION.md](VISION.md) §2.3） |
| 8 | 04-06 21:35 | 代码放置原则：复用 `boot_patch.rs`，不另开 | ✅ 继承（[PRINCIPLES.md](PRINCIPLES.md) §1） |
| 9 | 04-07 | 把"MagiskSU+KernelSU 双兼容管理器"当本质 § 1.1 | ❌ **当天即被推翻**（§ 14） |
| 10 | 04-07 | 加 `magisk_compat` 探测代码 | ⚠️ 已可用但收敛后冻结 |
| 11 | 04-07 | 写 §1.2"Magisk merge"作为下一焦点 | ❌ **当天即被推翻**（§ 14） |
| 12 | 04-07 | 改写 §1.1 为"简便化 root + Magisk→KernelSU 无缝迁移 + LKM/patch 互补" | ⚠️ 部分继承（隐含在 [VISION.md](VISION.md) §1） |
| 13 | 04-07 | rootmanagers/ 复用约定 + 脚本 | ✅ 继承（[PRINCIPLES.md](PRINCIPLES.md) §5） |
| 14 | 04-07 | **最终收敛**：`08-review-pause.md` + `00-vision.md` §1.3 — 不做双兼容、内核优先、汲取不融合 | ✅ **当前唯一方向** |

## 教训（已转化为 [PRINCIPLES.md](PRINCIPLES.md) §4）

1. **一天内 §1.1 / §1.2 / §1.3 三易其稿**：愿景反复对协作有害。
2. **从"统一架构"开始**：直接堆出 6 份大文档，反复推翻，沉没成本高。
3. **把厂商笔记抬成规格**：vivo `vermagic` / `vr` 等本应在 issue 跟踪，被加进了主架构文档。
4. **过早做"双兼容"产品形态**：不顾 magiskd 不向第三方 manager 授权的工程现实。
