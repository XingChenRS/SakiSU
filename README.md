# SakiSU

<img align="right" src="docs/SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

**简体中文** | [English](docs/README.md) | [vivo/iQOO 适配说明](docs/zh/vivo.md)

SakiSU 是基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 的独立下游分支，保留 KernelSU/SukiSU 系 root 管理、模块系统和 App Profile，并维护 SakiSU 自己的设备兼容、签名与发布策略。

## 项目状态

**SakiSU 已于 2026-08-01 恢复维护。** 项目保留上游来源与致谢，但不再承诺自动同步 ReSukiSU；上游改动会经过选择性审计后再引入，SakiSU 可以采用不同实现路线。

- 标准 `boot` / `init_boot` 修补继续使用单一通用 LKM，并由 `ksuinit` 在 `init_module` 阶段动态适配 vermagic。
- 选择或导入 `vendor_boot` 时，Manager 会按镜像 header 精确识别并进入独立 rmvr 流程，遍历全部 vendor ramdisk fragment，移除存在的 `vr.ko`、`vklp.ko` 及对应文本索引引用；该流程不会注入 KernelSU LKM，也不会修改 KSU/ADB 配置。
- SakiSU 正式 Manager 的证书信息以 `kernel/manager/manager_sign.h` 为唯一真值。`main` 与 tag 构建必须使用匹配的长期生产证书，最终 APK 还会再次校验证书摘要。
- 历史 `v4.2.0-sakisu.1` 的 APK 实际使用生产证书，但该 tag 中 Manager 的旧 UI 常量会误报“非官方”；修复已进入后续主线，新版本不得复用或移动旧 tag。

## 重点功能

- 内核级 `su` 与 root 授权管理。
- 模块系统、App Profile、SuSFS/tracepoint 等继承能力。
- 运行时 vermagic 自动适配，不恢复 `_vivo` LKM 或构建期硬编码 vermagic。
- vendor_boot rmvr：精确删除 `vr` / `vklp` 预置模块，兼容 vendor_boot v3、v4 和 v4 多 fragment。
- 正式签名闭环：生产 keystore、内核信任值、Manager 自检和最终 APK 证书保持一致。

## vendor_boot rmvr 使用方式

在 Manager 的安装页选择 `vendor_boot` 分区，或选择一个 header 为 vendor_boot 的镜像文件。Manager 会自动调用独立 rmvr 命令；选择普通 boot/init_boot 时仍走标准 LKM 修补流程。

rmvr 是对 vendor_boot 的写入操作。直接刷写前会在 `/data/adb/ksu/` 按原镜像 SHA-1 保存 `sakisu_vendor_boot_backup_*.img`，仍建议另行保留出厂镜像并确认分区和槽位。若目标模块及对应索引引用均不存在，SakiSU 会报告无需清理并保持镜像内容不变；清理非活动槽不会自动激活该槽。

## 签名迁移说明

旧版临时 CI 证书与当前 SakiSU 生产证书不同，Android 不允许不同证书直接覆盖安装，已经运行的旧内核也不会自动信任新证书。迁移时请先用旧的已授权 Manager 配置 Dynamic Manager，或先刷入信任当前生产证书的新 SakiSU 内核/LKM，再安装正式 Manager。

## 文档

- [English documentation](docs/README.md)
- [中文文档](docs/zh/README.md)
- [vivo/iQOO 适配说明](docs/zh/vivo.md)
- [SakiSU 产品与工程原则](docs/sakisu/README.md)
- [历史停更交接记录](HANDOFF.md)

## 鸣谢

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)：当前上游来源。
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：上游血统与兼容来源。
- [KernelSU](https://github.com/tiann/KernelSU)：内核级 root 方案基础。

仓库和 Manager 中保留的赞助入口用于支持上述上游作者与基础项目；SakiSU 当前没有独立募资入口。

## 许可证

`kernel` 目录遵循 GPL-2.0-only；其余部分按仓库内许可证声明执行。
