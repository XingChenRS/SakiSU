# Upstream Baseline

SakiSU is a downstream fork of ReSukiSU. This file locks the upstream baseline used by the current sync branch.

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

## How to refresh

1. Fetch upstream: `git fetch origin`
2. Create a new sync branch from the new `origin/main`
3. Replay SakiSU-specific commits in the order described by [`SAKISU-UPSTREAM-SYNC.md`](SAKISU-UPSTREAM-SYNC.md)
4. Update this file with the new baseline commit and sync branch name

## Related docs

- [`SAKISU-UPSTREAM-SYNC.md`](SAKISU-UPSTREAM-SYNC.md) — replay order and verification gates
- [`docs/sakisu/PROPOSAL.md`](docs/sakisu/PROPOSAL.md) — fork-and-inject strategy
- [`docs/sakisu/VENDOR-ADAPTATIONS.md`](docs/sakisu/VENDOR-ADAPTATIONS.md) — vendor-specific patches
