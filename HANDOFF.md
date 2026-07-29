# SakiSU 项目收手与交接

> 这是停止迭代后的存档说明：记录最终版本、独有改动、构建边界，以及
> `vr.ko` 上游 PR 的交付方式。
> 最后更新：2026-07-29。

---

## 1. 这是什么

**SakiSU** 是 **ReSukiSU 的下游 fork**（ReSukiSU 本身是 KernelSU 谱系的衍生）。形态为**内核优先的单一形态** root 方案：内核模块（LKM）+ ksud/ksuinit 用户态 + Android Manager。

| 项 | 值 |
|---|---|
| 最终 Release | `v4.2.0-sakisu.1`（versionCode 35026） |
| Android 包名 | `com.sakisu.sakisu` |
| 下游仓库 | `XingChenRS/SakiSU` |
| 上游仓库 | `ReSukiSU/ReSukiSU`（default 分支 `main`，只打 tag、不发 Release） |
| 历史上游基线 | 见 [`UPSTREAM.md`](UPSTREAM.md)（仅存档，不再刷新） |
| 主题 | vivo / iQOO GKI 适配（vermagic、`vr.ko` 拦截） |

工作区（`Desktop\sakisu\`）不是 git monorepo，真正的仓库在 `ReSukiSU/` 子目录。目录职责见根 [`README.md`](../README.md)。

---

## 2. 最终分支状态

| 分支 | 最终角色 | 处理方式 |
|---|---|---|
| `main` | SakiSU 最终存档主线 | 收手提交落定后冻结 |
| `pr/init-module-vr-filter` | 面向 ReSukiSU 的纯 `vr.ko` 补丁 | 由维护者推送 fork 并开上游 PR |
| `dev`、`sync/**` | 历史开发/同步分支 | 不再合入、不再自动构建；确认无需保留后再人工删除 |

最终用户版本锚点为 tag `v4.2.0-sakisu.1`。历史同步锚点
`sakisu-sync-baseline-20260721` 只用于追溯。

---

## 3. SakiSU 最终独有改动（存档）

以下是 SakiSU 相对当时上游的主要增量。旧的重放顺序仍保存在
[`SAKISU-UPSTREAM-SYNC.md`](SAKISU-UPSTREAM-SYNC.md)，但不再作为活跃维护流程。

1. **品牌 / 包名**：`com.sakisu.sakisu`；`settings.gradle.kts` 的 `rootProject.name`、`app/build.gradle.kts` 的 `namespace` / `archivesName`；README/docs。上游署名保留为致谢。
2. **vivo runtime 适配（全自动，运行时方案）**
   - `kernel/hook/init_module_filter.{c,h}`（**新增文件**）：hook arm64 `init_module`/`finit_module`，精确匹配 `.modinfo` 里 `name=vr` 的模块并「假装加载成功」而不真正加载；解析失败一律回退原 syscall。
   - `userspace/ksuinit/src/lib.rs`：首次 `init_module` 失败时读 `/dev/kmsg` 取内核要求的 vermagic，内存里改 `.modinfo` 重试。**一份通用 LKM 服务所有 KMI**，不要再引入 `_vivo` 构建变体、`boot-patch-vivo`、Manager 侧 vivo 开关。
3. **签名 / Manager 信任策略**（CVE-2023-46139 加固）：拒绝重复 v2 block、拒绝 v1-only 与 v1 降级、v3/v3.1 证书交叉校验；`kernel/manager/apk_sign.c` 与 ksud 语义保持一致。`manager_sign.h` 里 SakiSU 的 key 是**追加**的 `EXPECTED_*_SAKISU`（上游本就并列多家）。
4. **CI 定制**：`build-manager.yml` 的签名门禁。手动构建 `main` 时要求
   keystore 匹配 `EXPECTED_*_SAKISU`，否则失败；非生产引用可使用同批次临时签名。
5. **文档**：`DEVLOG-VIVO.md` 必须与 `init_module_filter.c` / `ksuinit/src/lib.rs` 的实现保持同步。

---

## 4. 收手说明（SakiSU 已停止迭代）

**结论：SakiSU 作为长期 fork 已收手，不再跟进上游、不再迭代。** 原因是本项目相对上游的两项核心增量都已有更好的归宿：

- **vermagic 运行时 fallback**：上游 ReSukiSU 已自行引入，SakiSU 不需要再扛。
- **vivo `vr.ko` 拦截**：已与上游开发者沟通并得到肯定答复；干净的 PR
  分支和文案已经准备，尚未推送或开 PR（见第 8 节）。一旦上游合入，
  vivo/iQOO 友好这件事就由上游原生承担。

因此**曾经规划的「上游自动跟进」机制已被彻底移除**（原 `upstream-sync.yml` + `SAKISU-AUTO-SYNC.md` 已删除）。不再有定时轮询、auto-merge、`sync/**` 分支流程；也**不需要** `SYNC_PAT` secret、分支保护 required checks 等一次性配置。

收手后仓库的状态：

- `main` 已冻结，**推送不再触发任何 CI**（build / lint / codeql / crowdin 都改为仅手动 `workflow_dispatch`；`release` 仅保留 tag 触发用于记录）。见第 5 节。
- 保留最后一轮 **v4.2.0-sakisu.1** 的 Release；不再发布独立 ksud CLI（避免误导用户以为有 CLI 安装路径，ksud 仍内嵌在 Manager APK 里）。
- 不再计划继续同步。若未来明确决定恢复维护，应新建分支重新评估，而不是恢复旧
  自动化。

---

## 5. 构建与签名

- 所有 build / lint / CodeQL / Crowdin 工作流均已取消普通 push、PR 和定时触发；
  只保留 `workflow_dispatch` 或内部 `workflow_call`。`release.yml` 只保留 tag 和手动
  触发。
- 手动构建 `main` 时仍要求仓库 secrets：`KEYSTORE`（base64 JKS）、
  `KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，且证书必须匹配
  `kernel/manager/manager_sign.h` 里的 `EXPECTED_SIZE_SAKISU` /
  `EXPECTED_HASH_SAKISU`。
- `ksud.yml` / `ksuinit.yml` 是 Manager APK 构建所需的内部可复用工作流，不代表
  对外提供独立 CLI。`release.yml` 只发布 Manager APK。

---

## 6. 已知坑 & 待办

**坑**
- **Windows `uapi` symlink**：`manager/app/src/main/cpp/uapi` 是指向仓库级 `uapi/` 的 git symlink。Windows 无 symlink 支持时本地原生构建会报 `uapi/ksu.h` 找不到（Kotlin 编译不受影响）；有 `scripts/fix-windows-uapi.ps1`，修复后勿把 junction 提交进去。Linux CI 正常。

**未完成项存档（不是继续迭代承诺）**
- Manager UI TODO：Settings 卸载流程、Theme 壁纸分离、FloatingBottomBar。
- 内核占位：`symbol_resolver.c` / `adb_root.c` / `ksud_integration.c` 等。
- 继承缺失项：`platform_probe.rs` 未恢复；SuSFS 多处 `TODO REFACTOR`（等上游 susfs-ksud 合并）。
- 文档补全：`docs/zh/vivo.md`、`docs/vivo.md` 等需反映运行时 vivo 方案。

---

## 7. 关键文件地图

| 用途 | 路径 |
|---|---|
| 历史上游基线 | `UPSTREAM.md` |
| 历史重放顺序 & 验证门 | `SAKISU-UPSTREAM-SYNC.md` |
| 上游 VR PR 文案 | `VR-FILTER-UPSTREAM-PR.md` |
| vivo 内核拦截 | `kernel/hook/init_module_filter.c` |
| vivo vermagic fallback | `userspace/ksuinit/src/lib.rs` |
| 签名策略（内核侧） | `kernel/manager/apk_sign.c` / `kernel/manager/manager_sign.h` |
| vivo 实现记录 | `DEVLOG-VIVO.md` |
| 产品愿景 | `docs/sakisu/VISION.md` / `PROPOSAL.md` |

---

## 8. `vr.ko` 上游 PR 交接

当前只在本地准备，**没有推送 fork，也没有向 ReSukiSU 创建 PR**。

| 项 | 值 |
|---|---|
| 本地分支 | `pr/init-module-vr-filter` |
| 上游基线 | 最新 `origin/main`（提交前应再次 fetch/rebase） |
| 变更范围 | `kernel/Kbuild`、`kernel/hook/init_module_filter.{c,h}`、`kernel/hook/syscall_hook_manager.c` |
| PR 文案 | [`VR-FILTER-UPSTREAM-PR.md`](VR-FILTER-UPSTREAM-PR.md) |
| 下游 fork remote | `sakisu` → `XingChenRS/SakiSU` |

提交前执行：

```bash
git fetch origin
git switch pr/init-module-vr-filter
git rebase origin/main
git diff --check origin/main...HEAD
git diff --stat origin/main...HEAD
git push -u sakisu pr/init-module-vr-filter
```

然后由维护者在 GitHub 创建
`XingChenRS/SakiSU:pr/init-module-vr-filter` → `ReSukiSU/ReSukiSU:main` 的 PR，
粘贴 PR 文案，并以该 PR 的上游 Actions 结果作为远程构建验证。
