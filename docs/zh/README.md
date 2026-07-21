# SakiSU

<img align="right" src="SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

**简体中文** | [English](../README.md) | [vivo/iQOO 适配教程](vivo.md)

SakiSU 是基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 的下游分支，保留 KernelSU/SukiSU 系 root 管理、模块系统、App Profile 等上游能力，并补充 vivo/iQOO 设备上的内核级 root 适配。

当前维护方式是：以 ReSukiSU 最新 `main` 为底，按主题重放 SakiSU 自己的改动。测试先进入 `dev` 或同步分支，稳定后再合入 `main`。

## 重点功能

- 内核级 `su` 与 root 授权管理。
- 模块系统、App Profile、SuSFS/tracepoint 等上游能力。
- **vivo/iQOO 全自动兼容**：运行时 vermagic 适配 + 内核级 `vr.ko` 拦截，无需修改 vendor_boot，无需 `_vivo` LKM 变体。
- CI 构建保留长期签名密钥优先、临时同批次签名兜底的策略。

## vivo/iQOO 快速理解

vivo/iQOO 兼容全自动，无需任何开关或特殊操作：

| 机制 | 作用 |
|---|---|
| 运行时 vermagic 适配（`ksuinit`） | 首次 `init_module` 失败后，读取内核日志提取所需 version magic，修补内存中模块的 `.modinfo` 后重试。单一通用 LKM 适配所有 KMI。 |
| 内核 `vr.ko` 拦截（`init_module_filter`） | Hook arm64 `init_module`/`finit_module`，对内部名称精确为 `vr` 的模块直接返回成功而不真正加载，阻断 vivo 反 root，无需冷移除 `vendor_boot`。 |

两者都在标准 `boot-patch` 流程中生效。用 Manager 修补 `init_boot.img`（选任意标准 KMI），刷入即可。

完整背景、风险说明和教程见 [vivo/iQOO 适配教程](vivo.md)。

## 文档入口

- [vivo/iQOO 适配教程](vivo.md)
- [英文文档](../README.md)
- [vivo 实现记录](../../DEVLOG-VIVO.md)
- [上游同步注意事项](../../SAKISU-UPSTREAM-SYNC.md)

## 鸣谢

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)：当前上游基底。
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：上游血统。
- [KernelSU](https://github.com/tiann/KernelSU)：内核级 root 方案基础。
- 感谢参与 vivo/iQOO 反 root 研究与验证的社区贡献者。

## 许可证

`kernel` 目录遵循 GPL-2.0-only；其余部分按仓库内许可证声明执行。
