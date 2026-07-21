# Vendor adaptations (sakisu)

> sakisu 在 ReSukiSU 上的厂商适配记录。每条记录最小化、可独立 revert。

## vivo / iQOO — 全自动内核级适配（现行方案）

vivo/iQOO 的 GKI 反 root 依赖两点：官方内核对 LKM 的 `vermagic` 强校验（包含 `vivo` token），以及 `vendor_boot` 中的反 root 模块 `vr.ko`。SakiSU 现在用**纯运行时**方式处理这两点，用户无需任何开关或额外操作，只需用标准 `boot-patch` 修补 `init_boot`。

### 1. 运行时 vermagic 适配（ksuinit）

**问题**：vivo 官方内核校验模块 `vermagic`，普通 GKI LKM 因 vermagic 不含设备实际串（如 `... vivo aarch64`）被拒载。

**方案**：`userspace/ksuinit/src/lib.rs` 在加载 KernelSU LKM 时：

1. 加载前以 `O_NONBLOCK` 打开 `/dev/kmsg`（回退 `/kmsg`）并 seek 到末尾。
2. 首次 `init_module` 成功则直接返回，不做任何改动。
3. 首次失败时，读取本次新增日志，仅当匹配 `version magic '...' should be '<kernel>'` 时提取内核要求的 vermagic。
4. 严格解析 ELF64 `.modinfo`，把新的 `vermagic=` 追加到 buffer 末尾并重定向 section header（`sh_offset`/`sh_size`），保持其余 section 偏移不变。
5. 用修补后的模块重试 `init_module`。
6. 非 vermagic 类错误、无 `.modinfo`、日志格式不符时不做泛化重试，返回原始错误。

**收益**：单一通用 LKM 适配所有 KMI，无需构建期 `_vivo` 变体，无需用户手选 KMI，跨内核小版本更新自动适配。

**改动文件**：`userspace/ksuinit/src/lib.rs`（对齐上游 ReSukiSU@83d1806）。

### 2. 内核 `vr.ko` 拦截（init_module_filter）

**问题**：`vendor_boot` 早期 init 会加载 `vr.ko`，它加载后隐藏自身并施加反 root 限制。旧方案冷移除 `vr.ko` 需重打包 `vendor_boot`，有变砖风险且随系统更新失效。

**方案**：`kernel/hook/init_module_filter.c` 在 KernelSU LKM 初始化时，用**直接 syscall table patch**（`ksu_syscall_table_hook`，非 tracepoint dispatcher）hook arm64 `__NR_init_module` 与 `__NR_finit_module`：

- 从用户 buffer / fd 按 offset 有界读取 ELF header、section table、`.modinfo`（不复制整个模块）。
- 提取 `.modinfo` 的 `name=` 值，精确等于 `vr` 时直接返回 0（假装加载成功）而不真正加载。
- 任何解析失败、名称不符、越界、内存不足都回退调用原始 syscall（fail-open）。

**前提**：KernelSU LKM 必须比 `vr.ko` 先加载（`init_boot` 注入的 KernelSU 在 vendor 模块加载前就绪）。因为直接改 syscall table，对**所有进程**（含 vendor init）生效，不依赖 tracepoint 进程标记。

**收益**：不修改 `vendor_boot` 分区，不冷移除，单一机制覆盖 `init_module`/`finit_module` 两条加载路径。

**改动文件**：`kernel/hook/init_module_filter.{c,h}`、`kernel/hook/syscall_hook_manager.c`（注册/注销）、`kernel/Kbuild`。

### 3. CI

单一通用 LKM 构建，无 `_vivo` 矩阵：`.github/workflows/ddk-lkm.yml`、`build-manager.yml`（已移除 `build-lkm-vivo.yml`）。

### 用户使用

vivo/iQOO 设备保持默认流程即可：

1. 用 Manager 修补 `init_boot.img`，KMI 选择任意标准项（无 `_vivo` 后缀）。
2. 刷回 `init_boot`：

```text
fastboot flash init_boot kernelsu_patched_xxx.img
fastboot reboot
```

ksuinit 会在首次加载时自动适配 vermagic，内核会自动拦截 `vr.ko`。无需单独处理 `vendor_boot`。

### 验证

正常启动后（拿到 root）内核日志应可见：

```text
init_module_filter: hooked init_module + finit_module
init_module_filter: blocked vr (init_module)   # 或 (finit_module)
Replaced module vermagic with kernel-required value: "..."   # 若默认 vermagic 不匹配
```

### 已知限制

1. **加载时序**：若某设备的 `vr.ko` 在 KernelSU LKM 之前加载，则拦截不生效；需确认 `init_boot` 的 KernelSU 早于 vendor 模块。
2. **仅 arm64**：`init_module_filter` 只 hook arm64，未覆盖其它架构。
3. **强制模块签名**：若目标内核 `CONFIG_MODULE_SIG_FORCE=y`，用户态改写模块可能影响验签，需在具体设备验证。
4. **模块名依赖**：拦截基于 `.modinfo` 的 `name=vr`；若厂商改名或加壳需另行适配。

---

## 历史方案（已移除，仅存档说明）

以下旧方案已在 SakiSU 中**完全移除**，仅记录以避免回退时重蹈覆辙：

- **构建期 `_vivo` LKM 注入**：`ddk-lkm.yml` 的 `vivo` 输入 + 硬编码/模板化 vermagic，产出 `<kmi>_vivo_kernelsu.ko`；独立 `build-lkm-vivo.yml` 矩阵。**问题**：需为每个 KMI 维护 vermagic 串，通用模板在真机常被拒载，用户须手选 `_vivo` KMI。已由运行时 vermagic 适配取代。
- **`vendor_boot` 冷移除 `vr.ko`（rmvr）**：`ksud boot-patch-vivo`/`patch_vivo()`/`remove_vendor_modules()` 删除 `vr.ko` 及 `modules.load`/`modules.dep`/`modules.softdep`/`modules.load.recovery` 引用；`boot-info classify-image` + Manager「去除vr或适配vivo特性」开关 + `_vivo` KMI 选择。**问题**：需重打包 `vendor_boot`，有变砖风险，随系统更新失效，且「两步刷机」易误操作。已由内核 `vr.ko` 拦截取代。

相关移除提交见 git 历史（`cleanup: remove build-time vivo vermagic injection`、`cleanup: remove legacy vivo/rmvr frontend`）。
