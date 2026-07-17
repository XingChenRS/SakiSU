# PRINCIPLES — 工程纪律

> 派生自上一轮 sakisu 的实战教训（[TIMELINE.md](TIMELINE.md)）。
> 重构启动后**任何**代码改动必须满足。

## 1. 代码放置

1. **能复用就不另开**：先看既有模块（boot patch / ksucalls / init event 等）是否已有能力。
2. **先看全链路再落点**：boot 镜像解析、ramdisk/cpio、KMI、刷写分区等，**统一以一处为事实来源**；编排层只调用，不复制解析逻辑。
3. **不把无关功能塞进无关模块**：例如不要在 manager UI 中实现 boot 镜像 patch；不要在编排层重复实现与底层工具相同的 cpio 规则。
4. **职责一图表**（重构后按此校核）：

   | 能力 | 归属 |
   |---|---|
   | Boot/init_boot 镜像 mmap / 解析 / patch / restore | `ksud/boot_patch.rs`（或等价底层） |
   | 内核 ioctl / supercall / fd | `ksud/.../ksucalls.rs` + `uapi/` |
   | init 阶段事件 / 模块脚本 / sepolicy 用户态 | `ksud/.../init_event.rs` 等 |
   | 决策树 / checkpoint / probe **编排** | `ksud/.../sakisu/`（**只调用，不解析**） |
   | Manager ↔ 内核 JNI | `manager/.../jni.c` + `Natives.kt` |

## 2. 隐蔽性（来自 [VISION.md](VISION.md) §2.1）

- 默认禁止常驻用户态服务；
- 优先内核 UAPI + 按需短生命周期执行；
- 新增接口必须自检：是否新增**固定路径 / 常驻进程 / 可枚举句柄 / 明显日志特征**。

## 3. 失败兜底（来自 [VISION.md](VISION.md) §2.2 / §2.3）

- 不假设回退总是可行 → 默认手动刷回兜底；
- patch 类默认拒绝二次 patch，需显式 `--force` 才继续；
- 所有错误必须落入标准化错误码（见 [DECISION-TREE.md](DECISION-TREE.md) §4）。

## 4. 文档纪律（教训 → 规则）

1. **不要把"线索"抬成"规格"**。厂商笔记 / 外部 issue 在 issue 区跟踪，不入主架构。
2. **不要在愿景文档里写产品形态反复**。一个版本就一句话；方向变更通过整文件覆盖，老版本归档或直接删除（保持文档树短）。
3. **不要从"统一架构"开始**。先做一件事做透，再考虑抽象。
4. **不要在 manager-side 强行兼容旧 ksud 子命令**。重构时一次性切干净。
5. **不要在第一阶段就引入备份决策**。不同进入路径（已有 Magisk vs 临时 root + 锁 BL）约束完全不同；备份是阶段性可选项，不是默认强制。

## 5. 第三方源码引入（继承自旧 `rootmanagers-notes.md`）

若需引入 KernelSU / Magisk / SKRoot / FolkPatch 等外部源码：

- **不**整树合并；放入 `third_party/<name>/` 并保留 LICENSE。
- 优先**白名单单文件复用**而非递归 copy。
- 在 `third_party/README.md` 记录每个文件的引入原因与上游 commit。
