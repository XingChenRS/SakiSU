# sakisu 产品文档

本目录是 SakiSU 的愿景、工程纪律与重构提案（由工作区 `sakisu/` 种子注入）。

## 文档导航

| 文件 | 职责 | 何时读 |
|---|---|---|
| [VISION.md](VISION.md) | **唯一**有效的产品愿景与硬约束 | 必读，第一份 |
| [PRINCIPLES.md](PRINCIPLES.md) | 工程纪律（代码放置 / 隐蔽性 / 保守 patch） | 写代码前 |
| [DECISION-TREE.md](DECISION-TREE.md) | 提权 4 阶段流水线 + 失败分类 + 回退兜底 | 设计编排层时 |
| [PROPOSAL.md](PROPOSAL.md) | 转向重构提案（舍弃项 / 继承项 / 汲取点 / 启动清单） | 重构启动时 |
| [VENDOR-ADAPTATIONS.md](VENDOR-ADAPTATIONS.md) | 厂商适配记录（vivo vermagic 等） | 接入新厂商时 |
| [TIMELINE.md](TIMELINE.md) | 历史方向反复的浓缩时间线（避坑用） | 想了解"为什么是这个方向"时 |

## 相关实现文档

- [Upstream baseline](../../UPSTREAM.md)
- [Upstream sync notes](../../SAKISU-UPSTREAM-SYNC.md)
- [vivo 实现笔记](../../DEVLOG-VIVO.md)
- [用户教程（中文）](../zh/vivo.md)

## 阅读顺序

```
VISION → PRINCIPLES → DECISION-TREE → PROPOSAL
                                    ↑
                          (TIMELINE 可作旁注)
```
