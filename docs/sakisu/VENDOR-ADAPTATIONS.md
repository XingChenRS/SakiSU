# 厂商适配记录

本文件描述 SakiSU 当前维护的厂商兼容边界。实现应尽量独立、可审计、可回退，并保持标准设备路径不受影响。

## vivo / iQOO

### 运行时 vermagic

标准 boot/init_boot 修补只使用通用 KMI LKM。`userspace/ksuinit/src/lib.rs` 在首次 `init_module` 因 version magic 不匹配而失败时，从新产生的内核日志中提取要求值，修正内存里的 `.modinfo` 并重试。非 vermagic 错误不盲目重试。

约束：

- 不恢复 `_vivo` LKM、`<kmi>_vivo_kernelsu.ko` 或独立构建矩阵。
- 不在 DDK workflow 中写死 UTS/vermagic。
- 动态修改只发生在加载缓冲区，不改仓库中的 ko 产物。

### vendor_boot rmvr

当 Manager 选中 `vendor_boot` 分区或导入的镜像 header 为 vendor boot 时，调用独立 `boot-patch-rmvr`：

- 只接受 `BootImageVersion::Vendor(_)`，普通 boot/init_boot 不进入该路径。
- vendor boot v3/单 ramdisk 走 `replace_ramdisk`；v4 table 遍历全部 fragment，只替换发生变化的条目。
- 按精确 basename 删除 `vr.ko`、`vklp.ko`，兼容常见压缩后缀。
- 对 `modules.load*`、`modules.dep`、`modules.softdep`、`modules.alias`、`modules.options`、`modules.blocklist`、`modules.order` 做 token 级清理，不用字符串包含判断。
- 不探测 KMI、不注入 `kernelsu.ko`/`ksuinit`、不修改 KSU/ADB 配置。
- 没有目标时保持原始镜像字节并跳过直刷；fragment 无法解析时失败退出。

rmvr 是写 vendor_boot 的后手，必须保留原始镜像和恢复路径。

### 运行时 vr 过滤

`kernel/hook/init_module_filter.c` 仍作为纵深防护：在 arm64 上覆盖 `init_module` 与 `finit_module`，仅当 `.modinfo` 的模块名精确为 `vr` 时阻止加载；解析失败时调用原 syscall。它不能替代所有早期预加载场景，因此与 rmvr 并存。

## 构建与签名

- `.github/workflows/ddk-lkm.yml` 只构建标准 LKM，历史 `inputs.vivo` 死代码已移除。
- Manager 的正式证书由 `kernel/manager/manager_sign.h` 单一提供给内核与 Gradle BuildConfig。
- main/tag 构建必须使用匹配的长期生产 keystore，并核验最终 APK 证书。

## 验证要求

- Rust 单测覆盖精确删除、相似名称保护、索引清理、无匹配幂等和 vendor header gate。
- GitHub Actions 覆盖 ksud、LKM 与 Manager 构建。
- 真机验证 vendor_boot v3/v4、多 fragment、A/B 槽位、运行时 vermagic 重试以及正式 Manager 授权。
