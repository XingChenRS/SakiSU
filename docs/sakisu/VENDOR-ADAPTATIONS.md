# Vendor adaptations (sakisu)

> sakisu 在 ReSukiSU 上的厂商适配记录。每条记录最小化、可独立 revert。

## 1. vivo — vermagic 注入 (LKM)

### 问题
vivo **所有 GKI 内核**都强校验 LKM 的 vermagic 字符串必须包含 `vivo` token，否则内核拒载。

### 方案
在 LKM 编译前注入含 `vivo` token 的 vermagic 字符串，覆盖 DDK 默认值。
**只动 CI YAML，不改内核源码、不破坏 ReSukiSU stock build。**

### 改动文件

| 文件 | 改动 | stock 影响 |
|---|---|---|
| `.github/workflows/ddk-lkm.yml` | 加 `vivo: bool` 输入；加 vermagic 注入 step（一个 step 内 case-by-KMI 处理全部 KMI）；产物加 `_vivo` 后缀；artifact 名加 `-vivo` 后缀 | `vivo: false`（默认）逻辑等价于改动前，零影响 |
| `.github/workflows/build-lkm-vivo.yml` | **新增**。sakisu 专属 caller，矩阵覆盖**所有 KMI** | 完全独立 |
| `.github/workflows/build-manager.yml` | 加 `build-lkm-vivo` 并行 job + `paths` 触发 | stock build-lkm job 保持不变 |

### 覆盖矩阵（与 stock `build-lkm.yml` 对齐）

| KMI | UTS_RELEASE 来源 | UTS 同步 patch | 备注 |
|---|---|---|---|
| `android12-5.10` | 设备实测 | 否 | `5.10.233-android12-9-g28b19682acbc-dirty` |
| `android13-5.10` | 设备实测（共用 5.10） | 否 | 同上 |
| `android13-5.15` | 通用模板 | 是 | `5.15.0-android13-0-maybe-dirty`（可能与实机不同） |
| `android14-5.15` | 通用模板 | 是 | `5.15.0-android14-0-maybe-dirty` |
| `android14-6.1` | 设备实测 | 是 | `6.1.145-android14-11-maybe-dirty` |
| `android15-6.6` | 通用模板 | 是 | `6.6.0-android15-0-maybe-dirty` |
| `android16-6.12` | 通用模板 | 是 | `6.12.0-android16-0-maybe-dirty` |

> **设备实测 vs 通用模板**：实测项是 sakisu 文档作者从 vivo 真机采到的字符串；通用模板假设 vivo 仅做 substring 校验（contains 'vivo'），UTS 部分填占位值。**若通用模板的 KMI 在真机上仍被拒载**，需采集真机 vermagic 后在 `ddk-lkm.yml` case 段加分支。

### 产物与命名

| 类型 | artifact 名 | 文件名 |
|---|---|---|
| stock | `<kmi>-lkm` | `<kmi>_kernelsu.ko` |
| vivo  | `<kmi>-vivo-lkm` | `<kmi>_vivo_kernelsu.ko` |

### 用户使用说明（重要）

vivo .ko 与 stock .ko 同时打进 manager APK（`ksud` 通过 `RustEmbed` 嵌入 `userspace/ksud/bin/aarch64/` 全量 `*_kernelsu.ko`），manager `list_supported_kmi()` 把它们都列出来：

```
android12-5.10
android12-5.10_vivo
android13-5.10
android13-5.10_vivo
...
```

**vivo 设备用户：在 manager 的 "Select KMI" 选择框里手动选带 `_vivo` 后缀的项**。

原因：[boot_patch.rs::get_current_kmi()](../ReSukiSU/userspace/ksud/src/boot_patch.rs) 仅从 `uname -r` / 已加载模块解析 KMI，**无法区分 stock 与 vivo 变体**。如果它返回 `android12-5.10`，manager 会自动用 stock .ko，在 vivo 设备上 `insmod` 时会因 vermagic 不含 `vivo` 被拒载。手选 `_vivo` 变体即可绕过。

后续若要做"vivo 设备自动选 vivo 变体"，需改 `get_current_kmi()` 探测 `ro.product.manufacturer`，属于侵入 ReSukiSU 的改动，按 [PROPOSAL.md](PROPOSAL.md) §1 寄生原则当前不做。

### release 集成
`build-lkm-vivo` 已挂在 `build-manager.yml`，与 stock LKM 并行。`release.yml` 现有 glob `android*-lkm/*_kernelsu.ko` **自动捕获** vivo artifacts（通配符匹配 `*-vivo-lkm/`），发布产物自动包含 vivo .ko。

---

## 2. vivo — vendor_boot rmvr (vr.ko 触发崩溃环)

### 问题
vivo / iQOO 设备的 `vendor_boot` 分区里有个名为 `vr.ko` 的厂商模块。一旦 `vendor_boot.img` 经过 KernelSU 标准 `boot-patch` 流程（哪怕只是 cpio 重打包，没改任何模块），重新刷回去之后 `vr.ko` 在 init 早期 `modprobe` 时崩溃，触发 boot loop。

vivo 自家的解决脚本（`mk\.sh:process_crash_vr()`）做法是：**unpack vendor_boot → 把 vr.ko 从 ramdisk 删掉 → 同步从 `modules.load` / `modules.softdep` / `modules.load.recovery` 里把 `vr` 那行删掉 → repack**。不注入任何 KernelSU 组件，因为 KernelSU 本身住在 `init_boot`。

### 关键陷阱（设计教训）

```
vivo 设备完整刷机 = 两步：
  step 1: init_boot.img  -> 标准 KernelSU boot-patch（注入 LKM，KMI 选 *_vivo）
  step 2: vendor_boot.img -> rmvr only（删 vr.ko，绝对不要注 LKM）
```

如果把这两步揉成一个开关（"vivo 模式 ON = 只做 rmvr"），**用户就再也没办法装 LKM 了**。早期 sakisu 实现就踩了这个坑：vivo 开关 ON → manager 强制 `partition=vendor_boot` → 用户选 `init_boot.img` 也被当成 vendor_boot 处理 → LKM 永远装不进去。

### 最终方案（cpio 内容自动判别）

**单一开关，两条路径，由 ksud 根据 cpio 内容自动选**：

| 用户操作 | ksud 看到的 cpio | 走向 |
|---|---|---|
| vivo 开关 ON + 选 `init_boot.img` | 没有 `lib/modules/*.ko` | 标准 LKM 注入（KMI 自动加 `_vivo`） |
| vivo 开关 ON + 选 `vendor_boot.img` | 有 `lib/modules/*.ko` | rmvr only（自动 `no_install=true`，删 vr.ko） |
| vivo 开关 OFF | 任意 | 完全等同上游 KernelSU 行为 |

### 改动文件

| 文件 | 改动 | stock 影响 |
|---|---|---|
| `userspace/ksud/src/boot_patch.rs` | `patch_vivo()` 简化为"加 vr.ko 到 remove_module + 调 patch()"。`patch()` 在 cpio 加载后扫描，命中 `lib/modules/*.ko` 就 `no_install=true` 跳 LKM。`remove_vendor_modules()` 动态扫 `lib/modules/<X.YZ>-gki/` 子目录（不再硬编码 `6.1-gki`） | 上游 `boot-patch` 路径不变；`boot-patch-vivo` 是 sakisu 新增子命令 |
| `userspace/ksud/src/android/cli.rs` | 注册 `BootPatchVivo` 子命令 | 子命令名独立 |
| `manager/.../KsuCli.kt` | vivo 开关 ON → 总走 `boot-patch-vivo`，**不依赖 partition 字段** | OFF 时行为完全等同上游 |
| `manager/.../Install.kt` | 不再因 vivo 开关强制 `partition=vendor_boot`；不再因 vivo 开关屏蔽 KMI 对话框；`preferVivoKmi = enableVivoPatch`（vivo ON 时 KMI 默认带 `_vivo` 后缀） | OFF 时无差异 |

### 关键代码（ksud `patch()` 自动判别）

```rust
// boot_patch.rs, after cpio is loaded
if !no_install {
    let looks_like_vendor = cpio
        .entries()
        .keys()
        .any(|p| p.starts_with("lib/modules/") && p.ends_with(".ko"));
    if looks_like_vendor {
        println!("- Auto-detected vendor_boot (lib/modules/*.ko present); skipping LKM injection");
        no_install = true;
    }
}
```

### 关键代码（rmvr 动态扫 GKI 子目录）

```rust
// 动态发现 lib/modules/<ver>-gki，覆盖所有 GKI 版本（5.10/5.15/6.1/6.6/...）
let mut module_roots: Vec<String> = vec!["lib/modules".to_string()];
for path in cpio.entries().keys() {
    if let Some(rest) = path.strip_prefix("lib/modules/") {
        let head = rest.split('/').next().unwrap_or("");
        if head.ends_with("-gki") && !module_roots.iter().any(|r| r.ends_with(head)) {
            module_roots.push(format!("lib/modules/{head}"));
        }
    }
}
```

> 早期硬编码 `["lib/modules", "lib/modules/6.1-gki"]` 导致 5.10 / 5.15 / 6.6 设备完全没生效——脚本看似跑了但 vr.ko 还在。

### 用户使用说明

vivo 设备**保持 vivo 开关常开**，需要刷哪个分区就选哪个 `.img`：
1. 装 KernelSU：选 `init_boot.img`，按 KMI 弹框选 `_vivo` 变体
2. 防 boot loop：选 `vendor_boot.img`，KMI 选什么都行（会被忽略）

### 验证路径

正常 init_boot 注入应看到：
```
- Mode: vivo compat (auto-detect init_boot vs vendor_boot)
- Adding KernelSU LKM
- KMI: android14-6.1_vivo
```

正常 vendor_boot rmvr 应看到：
```
- Mode: vivo compat (auto-detect init_boot vs vendor_boot)
- Auto-detected vendor_boot (lib/modules/*.ko present); skipping LKM injection
- Detected vendor module root: lib/modules/<你的GKI版本>-gki
- Removing vendor module lib/modules/<...>/vr.ko
- Cleaning reference in lib/modules/<...>/modules.load for vr.ko
- Cleaning reference in lib/modules/<...>/modules.softdep for vr.ko
```

### 同期踩过的隐蔽 bug（与本节相关，已修）

- `kernel/manager/throne_tracker.c::my_actor()` 的 `strncmp(name, "base.apk", 8)` 是**前缀匹配**，会把同目录的 `base.apk.prof` 当成 APK 走签名校验，导致用户应用全部 `is_manager=0`，release 包永远显示"未安装"。**必须用 `namelen == 8 && memcmp(...)`**。vivo 系统应用没 ART profile 所以幸运逃过，问题只在用户应用上爆。


### 触发方式
- 自动：push 到 main/dev/ci 或 PR → 触发 `build-manager` → 触发 `build-lkm-vivo`
- 手动：Actions → "Build LKM for KernelSU (vivo / sakisu)" → Run workflow
- Release：tag `v*` → release pipeline → 含 vivo .ko

### 已知限制 / 风险
1. **vermagic 字符串硬编码**：vivo 升级官核版本号会失效，需手工同步。
2. **通用模板未在真机验证**：5.15 / 6.6 / 6.12 用的是占位 UTS_RELEASE；若 vivo 做严格全字符串匹配，会失败。
3. **`-dirty` / `maybe-dirty` 后缀**：表示采集自非纯净源码树，覆盖范围为"采样时刻可加载"。
4. **未做运行时能力探测**：`get_current_kmi()` 不识别厂商变体，vivo 设备用户必须在 manager 的 "Select KMI" 选择框里手选 `_vivo` 后缀的 KMI；详见上方"用户使用说明"。

### 与 [VISION.md](VISION.md) / [PRINCIPLES.md](PRINCIPLES.md) 的对齐
- 符合 [VISION.md](VISION.md) §1 主线（内核级 root）— 让 LKM 路线在 vivo 上可用。
- 符合 [VISION.md](VISION.md) §3 厂商对抗"线索"原则 — vivo `vermagic` 这条线索现在变成具体落地项；不抬成全仓规格。
- 符合 [PRINCIPLES.md](PRINCIPLES.md) §1 代码放置 — 改动只在 CI YAML 与 sakisu 文档；不污染 kernel/userspace 主代码。

### 回退（清理路径）
1. `git rm .github/workflows/build-lkm-vivo.yml`
2. `build-manager.yml` 中删除 `build-lkm-vivo:` job 块 + `paths` 中的 `build-lkm-vivo.yml` 行
3. `ddk-lkm.yml` 中删除 `vivo:` 输入声明、`Inject Vivo vermagic` step、`SUFFIX` 逻辑、artifact name 中的三元表达式
4. 删除本文件本节
