# SakiSU

<img align="right" src="docs/SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

**简体中文** | [English](docs/README.md) | [vivo/iQOO 适配教程](docs/zh/vivo.md)

SakiSU 是基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 的下游分支，保留 KernelSU/SukiSU 系 root 管理、模块系统和 App Profile，并重点补充 vivo/iQOO 设备上的内核级 root 适配。

## 项目状态

**SakiSU 已于 2026-07-26 停止迭代。** 仓库保留最后一轮
`v4.2.0-sakisu.1` Release，不再自动跟进上游，也不再从 `main` 的普通
推送触发构建或发布。

- 运行时 vermagic fallback 已由 ReSukiSU 上游原生实现。
- `vr.ko` 加载拦截已整理为不含 SakiSU 品牌改动的独立上游补丁，待维护者提交给
  ReSukiSU。
- GitHub Release 只保留 Manager APK；不再单独发布 ksud CLI，避免造成存在独立
  CLI 安装路径的误解。ksud 仍作为 Manager 的内部组件构建和打包。

收手背景、最终分支状态和上游 PR 交接见 [HANDOFF.md](HANDOFF.md)。

## 重点功能

- KernelSU/SukiSU 系内核级 `su` 与 root 授权管理。
- 模块系统、App Profile、SuSFS/tracepoint 等上游能力。
- **vivo/iQOO 兼容**：运行时 vermagic 适配 + 内核 vr.ko 拦截，单一标准 LKM。
- 构建工作流保留用于必要的手动验证，但不再随分支推送自动运行。

## vivo/iQOO 快速说明

SakiSU 为 vivo/iQOO 设备提供自动化兼容：

- **运行时 vermagic 适配**：ksuinit 在模块加载失败时自动读取内核日志并修正 vermagic 后重试。
- **内核 vr.ko 拦截**：通过 arm64 `init_module`/`finit_module` syscall hook 精确阻止 `vr` 模块加载，无需修改 vendor_boot。
- **单一标准 LKM**：所有设备使用相同 KernelSU 模块，无需 `_vivo` 变体。

兼容逻辑全自动，无需任何开关：用 Manager 修补 `init_boot.img`（选任意标准 KMI）并刷入即可。

完整背景、风险说明和教程见 [docs/zh/vivo.md](docs/zh/vivo.md)。

## 文档

- [中文文档](docs/zh/README.md)
- [English documentation](docs/README.md)
- [vivo/iQOO 适配教程](docs/zh/vivo.md)
- [vivo/iQOO compatibility guide](docs/vivo.md)
- [vivo 实现记录](DEVLOG-VIVO.md)
- [项目收手与上游 PR 交接](HANDOFF.md)

## 鸣谢

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)：当前上游基底。
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：上游血统。
- [KernelSU](https://github.com/tiann/KernelSU)：内核级 root 方案基础。
- 感谢参与 vivo/iQOO 反 root 研究与验证的社区贡献者。

## 许可证

`kernel` 目录遵循 GPL-2.0-only；其余部分按仓库内许可证声明执行。
