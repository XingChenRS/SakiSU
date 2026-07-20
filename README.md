# SakiSU

<img align="right" src="docs/SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

**简体中文** | [English](docs/README.md) | [vivo/iQOO 适配教程](docs/zh/vivo.md)

SakiSU 是基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 的下游分支，保留 KernelSU/SukiSU 系 root 管理、模块系统和 App Profile，并重点补充 vivo/iQOO 设备上的内核级 root 适配。

当前同步策略是：以 ReSukiSU 最新 `main` 为底，按主题重放 SakiSU 自己的改动。测试先进入 `dev` 或同步分支，稳定后再合入 `main`。

## 重点功能

- KernelSU/SukiSU 系内核级 `su` 与 root 授权管理。
- 模块系统、App Profile、SuSFS/tracepoint 等上游能力。
- **vivo/iQOO 兼容**：运行时 vermagic 适配 + 内核 vr.ko 拦截，单一标准 LKM。
- CI 保留长期签名密钥优先、临时同批次签名兜底的构建流程。

## vivo/iQOO 快速说明

SakiSU 为 vivo/iQOO 设备提供自动化兼容：

- **运行时 vermagic 适配**：ksuinit 在模块加载失败时自动读取内核日志并修正 vermagic 后重试。
- **内核 vr.ko 拦截**：通过 arm64 `init_module` syscall hook 精确阻止 `vr` 模块加载。
- **单一标准 LKM**：所有设备使用相同 KernelSU 模块，无需 `_vivo` 变体。

Manager 中的 vivo 开关「去除vr或适配vivo特性」控制兼容逻辑。

完整背景、风险说明和教程见 [docs/zh/vivo.md](docs/zh/vivo.md)。

## 文档

- [中文文档](docs/zh/README.md)
- [English documentation](docs/README.md)
- [vivo/iQOO 适配教程](docs/zh/vivo.md)
- [vivo/iQOO compatibility guide](docs/vivo.md)
- [vivo 实现记录](DEVLOG-VIVO.md)
- [上游同步注意事项](SAKISU-UPSTREAM-SYNC.md)

## 鸣谢

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)：当前上游基底。
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：上游血统。
- [KernelSU](https://github.com/tiann/KernelSU)：内核级 root 方案基础。
- 感谢参与 vivo/iQOO 反 root 研究与验证的社区贡献者。

## 许可证

`kernel` 目录遵循 GPL-2.0-only；其余部分按仓库内许可证声明执行。
