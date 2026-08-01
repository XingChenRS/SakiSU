# SakiSU

<img align="right" src="SakiSU_blue.svg" width="220px" alt="SakiSU Icon">

**简体中文** | [English](../README.md) | [vivo/iQOO 适配说明](vivo.md)

SakiSU 是基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) 的独立下游分支，保留 KernelSU/SukiSU 系 root 管理、模块系统、App Profile 等能力，并维护自己的设备兼容、签名和发布策略。

## 维护状态

**SakiSU 已于 2026-08-01 恢复维护。** 项目保留上游来源与致谢，但不自动镜像 ReSukiSU；上游改动会经过选择性审计，SakiSU 也可以采用不同的实现路线。

当前镜像修补分为两条互不混用的路径：

| 输入 | 行为 |
|---|---|
| `boot` / `init_boot` | 标准 LKM 修补。`ksuinit` 保留运行时 vermagic fallback，在首次 `init_module` 失败后按内核日志修正内存中的 `.modinfo` 并重试。 |
| `vendor_boot` | 按 header 精确进入 rmvr，遍历全部 vendor ramdisk fragment，删除存在的 `vr.ko`、`vklp.ko` 和对应文本索引引用；不注入 KernelSU LKM，也不修改 KSU/ADB 配置。 |

Manager 会根据所选分区或镜像 header 自动路由。SakiSU 不恢复 `_vivo` LKM 产物，也不恢复构建期硬编码 vermagic。

## 正式签名

`kernel/manager/manager_sign.h` 是 SakiSU 正式 Manager 证书的唯一真值。Gradle 从该文件生成 Manager 自检字段；main 和 tag 工作流缺少匹配的长期生产 keystore 时会直接失败，最终重打包 APK 还会再次核验证书摘要。

历史 `v4.2.0-sakisu.1` APK 的实际生产签名正确，但该 tag 中 Manager UI 仍保留旧证书常量，因此可能误报“非官方”。后续主线已修正；新的单一来源规则会阻止这种分裂再次发生。

旧版临时 CI 证书不能直接覆盖升级到生产证书，已经运行的旧内核也不会自动信任新证书。迁移时请先用旧的已授权 Manager 配置 Dynamic Manager，或先安装信任当前生产证书的新 SakiSU 内核/LKM，再安装正式 Manager。

## 安全提示

修改 vendor_boot 具有设备风险。刷写前请保留原始镜像并确认分区、槽位正确。若目标模块和索引引用都不存在，rmvr 会报告无需清理并保持原始镜像字节不变。

## 文档入口

- [vivo/iQOO 适配说明](vivo.md)
- [English documentation](../README.md)
- [SakiSU 产品与工程原则](../sakisu/README.md)
- [历史上游同步说明](../archive/SAKISU-UPSTREAM-SYNC.md)
- [历史停更交接记录](../../HANDOFF.md)

## 鸣谢

- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)：当前上游来源。
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：上游血统与兼容来源。
- [KernelSU](https://github.com/tiann/KernelSU)：内核级 root 方案基础。

## 许可证

`kernel` 目录遵循 GPL-2.0-only；其余部分按仓库内许可证声明执行。
