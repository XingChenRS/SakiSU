# SakiSU 产品与工程文档

本目录记录 SakiSU 的产品愿景、工程纪律和厂商适配决策。SakiSU 已于 2026-08-01 恢复维护，这些文档重新作为当前开发的约束与决策依据；带有“历史”说明的章节仍只用于追溯。

## 文档导航

| 文件 | 职责 | 何时阅读 |
|---|---|---|
| [VISION.md](VISION.md) | 产品愿景与硬约束 | 确认方向时 |
| [PRINCIPLES.md](PRINCIPLES.md) | 工程纪律、代码放置、隐藏性和保守 patch 原则 | 写代码前 |
| [DECISION-TREE.md](DECISION-TREE.md) | 提权阶段、失败分类与回退兜底 | 设计流程时 |
| [PROPOSAL.md](PROPOSAL.md) | 历史重构提案和取舍记录 | 回顾决策时 |
| [VENDOR-ADAPTATIONS.md](VENDOR-ADAPTATIONS.md) | 厂商适配边界，包括动态 vermagic 与 vendor_boot rmvr | 接入适配时 |
| [TIMELINE.md](TIMELINE.md) | 历史方向变化的压缩时间线 | 追溯原因时 |

## 当前实现边界

- 标准 boot/init_boot 使用通用 LKM 与运行时 vermagic 修正。
- vendor_boot 使用独立 rmvr 路径，精确删除 `vr` / `vklp`，不注入 LKM。
- 不恢复 `_vivo` 构建产物和构建期硬编码 vermagic。
- SakiSU 保持独立下游路线；上游改动只做选择性审计，不启用自动同步。
- 正式 Manager 的生产证书以 `kernel/manager/manager_sign.h` 为唯一真值。

## 相关实现文档

- [历史上游基线](../archive/UPSTREAM-BASELINE.md)
- [历史上游同步说明](../archive/SAKISU-UPSTREAM-SYNC.md)
- [vivo 实现笔记](../../DEVLOG-VIVO.md)
- [中文用户说明](../zh/vivo.md)
