# Upstream Baseline

> **Archived on 2026-07-26.** SakiSU is frozen and no longer follows
> ReSukiSU. The values below document the final historical sync baseline; do
> not refresh them as an active maintenance process.

SakiSU is a downstream fork of ReSukiSU. This file records the upstream
baseline used by its historical sync branch.

## Remotes

| Remote | URL | Role |
|---|---|---|
| `origin` | https://github.com/ReSukiSU/ReSukiSU | Upstream |
| `sakisu` | https://github.com/XingChenRS/SakiSU.git | Downstream (this project) |

## Locked baseline

| Field | Value |
|---|---|
| Upstream ref | `origin/main` |
| Baseline commit | `e8f607a2cb1eb6f153809987eccd0d7a40ea1f70` |
| Baseline subject | `manager: implement dynamic manager settings, aspect-locked cropping and other minimal fixes` |
| Sync branch | `sync/resukisu-main-20260705` |
| Sync HEAD (as of 2026-07-21) | `0f6a33f15c0b709a74c5376004457f8374d89e93` |
| Commits ahead of baseline | 10 |
| Local baseline tag | `sakisu-sync-baseline-20260721` |

## Archive policy

The SakiSU fork is no longer refreshed. The only remaining upstream-bound work
is the self-contained `vr.ko` filter described in
[`VR-FILTER-UPSTREAM-PR.md`](../../VR-FILTER-UPSTREAM-PR.md).

## Related docs

- [`SAKISU-UPSTREAM-SYNC.md`](SAKISU-UPSTREAM-SYNC.md) — archived replay order and verification gates
- [`VR-FILTER-UPSTREAM-PR.md`](../../VR-FILTER-UPSTREAM-PR.md) — prepared upstream PR description
- [`PROPOSAL.md`](../sakisu/PROPOSAL.md) — fork-and-inject strategy
- [`VENDOR-ADAPTATIONS.md`](../sakisu/VENDOR-ADAPTATIONS.md) — vendor-specific patches
