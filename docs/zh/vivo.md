# vivo/iQOO 兼容说明

SakiSU 在标准 LKM 路径中保留运行时 vermagic 动态适配，同时恢复独立的 vendor_boot rmvr，用于处理厂商预置且可能影响 KernelSU 正常工作的模块。

## 两条互不混用的路径

1. 修补 `boot` 或 `init_boot` 安装标准 `kernelsu.ko`。若第一次 `init_module` 报 vermagic 不匹配，`ksuinit` 会读取内核日志、在内存中修正模块并重试；不再构建 `_vivo` LKM。
2. 选择 `vendor_boot` 分区或导入 vendor_boot 镜像执行 rmvr。SakiSU 会校验 vendor boot header，遍历所有 ramdisk fragment，删除存在的 `vr.ko`、`vklp.ko` 和支持的文本模块索引中的精确引用。该路径不会安装 KernelSU，也不会修改 KSU/ADB ramdisk 配置。

SakiSU 仍保留运行时精确阻止 `vr` 加载的实现作为后手；当预置模块在运行时路径生效前已产生干扰时，冷移除仍有价值。

## 推荐操作

1. 备份当前槽位的原始 boot、init_boot 和 vendor_boot 镜像。
2. 先在 SakiSU Manager 中修补或安装到正常的 boot/init_boot 分区。
3. 返回安装页，选择 `vendor_boot` 后再次执行，以完成 rmvr。若采用文件修补，选择 vendor_boot 镜像即可，Manager 会自动识别 header。
4. 检查刷写日志。未发现目标是成功的无操作，SakiSU 不会重复刷写未改变的镜像。
5. 两项操作都成功后再重启。

## 风险与恢复

不同设备的 vendor boot 布局可能不同。SakiSU 支持单 ramdisk（vendor boot v3 及无表布局）和 v4 表中的全部 fragment；若遇到损坏或无法解析的非 CPIO fragment，会直接失败而不是静默漏删。务必保留原始镜像和可用的 recovery/fastbootd 恢复方式，不要把未识别为 vendor_boot 的输出刷入该分区。

## 签名迁移

若旧版 Manager 使用临时 CI 证书，请在旧 Manager 仍获授权时配置 Dynamic Manager，或先安装信任当前生产证书的新 SakiSU 内核/LKM，再替换 APK。Android 和已经运行的旧内核都不会自动接受不同的签名证书。
