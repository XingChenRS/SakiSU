# Fix Windows Git Symlinks (local only)

On Windows, Git often checks out symlinks as plain text files. This breaks
`manager/app/src/main/cpp/uapi` (should point at the repo-root `uapi/` headers).

Run from repository root in an elevated or Developer-capable PowerShell:

```powershell
./scripts/fix-windows-uapi.ps1
```

This replaces the broken text file with a directory junction. Git may report
the path as modified; do not commit the junction. Restore with:

```powershell
git checkout -- manager/app/src/main/cpp/uapi
```
