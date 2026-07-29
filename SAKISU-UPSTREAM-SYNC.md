# SakiSU Upstream Sync Notes

> **Historical record only.** SakiSU stopped following upstream on
> 2026-07-26. This document is retained to explain the old replay order; it is
> not an active sync plan and no workflow consumes it.

This historical branch was rebuilt from the ReSukiSU `origin/main` tip
available at the time.

Target branch:

```text
sync/resukisu-main-20260705
```

## Historical Goal

Replay SakiSU-specific work on top of ReSukiSU mainline in a clean,
reviewable order without hard-merging the old SakiSU branch history.

## Replay Order

1. Branding and package migration
   - Project name: `SakiSU`
   - Android package namespace: `com.sakisu.sakisu`
   - GitHub repository links: `XingChenRS/SakiSU`
   - Keep ReSukiSU as upstream credit only.

2. vivo/iQOO support (fully automatic, runtime approach)
   - Runtime vermagic fallback in `ksuinit`: on the first `init_module`
     failure, read `/dev/kmsg`, extract the kernel-required version magic,
     patch the in-memory module `.modinfo`, and retry. One universal LKM
     serves every KMI; do not reintroduce `_vivo` build variants.
   - Kernel `vr.ko` blocking in `init_module_filter.c`: hook arm64
     `init_module`/`finit_module` via direct syscall-table patching and
     return success for the module whose `.modinfo` `name=` is exactly
     `vr`, without loading it. Any parse failure falls through to the
     original syscall.
   - Do not reintroduce cold removal of `vr.ko` from `vendor_boot`, the
     `boot-patch-vivo` subcommand, `boot-info classify-image`, or any
     manager-side vivo switch / `_vivo` KMI selection. Standard
     `boot-patch` on `init_boot` is the only flow.

3. Signing and manager trust policy
   - Do not return to forced v2-only APK signing.
   - Reject duplicate v2 signature blocks (only the first is authoritative,
     matching Android); reject v1-only APKs and v1-downgrade attacks.
   - When v3 or v3.1 blocks are present, their certificates must also be
     trusted (cross-verify against the same trust list). Kernel and ksud
     must stay in sync (see CVE-2023-46139 / GHSA-86cp-3prf-pwqq).
   - Keep exact `base.apk` tracking; do not accept `base.apk.prof`,
     `base.apk.idsig`, or sibling artifacts as manager APKs.

4. CI behavior
   - The final repository state disables ordinary push, pull-request, and
     scheduled triggers. Validation is manual or invoked internally with
     `workflow_call`.
   - Long-lived keystore secrets are preferred when present.
   - Missing keystore secrets fall back to a self-consistent ephemeral key
     (allowed on `dev` and test branches; only `main` enforces production).
   - Do not pass repository signing secrets through job outputs; only
     generated ephemeral keys may be shared that way.
   - Crowdin must skip cleanly when Crowdin secrets are absent.
   - Build one universal LKM per KMI (no `_vivo` matrix).

5. Documentation
   - Root `README.md` must exist.
   - `docs/README.md`, `docs/zh/README.md`, `docs/vivo.md`, and
     `docs/zh/vivo.md` must describe the current automatic runtime behavior.
   - `DEVLOG-VIVO.md` records implementation details and must stay aligned
     with `userspace/ksuinit/src/lib.rs` and
     `kernel/hook/init_module_filter.c`.

## Verification Gates

- `git diff --check`
- Markdown local link and image reference check
- `cargo fmt --manifest-path userspace/ksud/Cargo.toml -- --check`
- `cargo fmt --manifest-path userspace/ksuinit/Cargo.toml -- --check`
- `cargo check --manifest-path userspace/ksud/Cargo.toml`
- `cargo check --manifest-path userspace/ksuinit/Cargo.toml`
- `./gradlew :app:compileDebugKotlin` from `manager` when an Android SDK is available
- Historical GitHub Actions verification list (now manually dispatched only):
  - Build Manager
  - Build SU
  - Clippy check
  - Rustfmt check
  - ClangFormat check
  - ShellCheck
  - Crowdin Action

## Local Windows Note

`manager/app/src/main/cpp/uapi` is a Git symlink to the repository-level `uapi` directory. On Windows checkouts without symlink support, full native Manager builds can fail with `uapi/ksu.h` not found even though Kotlin compilation succeeds. Linux GitHub Actions should resolve the symlink normally.

## Production Signing Gate

Release branches (`main`, `dev`) require repository secrets:

- `KEYSTORE` (base64 JKS)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The signing certificate **must** match `EXPECTED_SIZE_SAKISU` / `EXPECTED_HASH_SAKISU` in `kernel/manager/manager_sign.h`. Otherwise CI fails and must not ship `IS_PR_BUILD` or bake `EXPECTED_PR_BUILD_*` into LKM (that is what shows the home-page “PR debug build” warning).

`sync/**` may still use ephemeral signing for try builds; those artifacts will show the PR debug warning until production secrets are configured.
