# Replace broken uapi symlink text-file with a Windows junction.
# Safe for local builds; do not commit the result.
$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$linkPath = Join-Path $repoRoot "manager\app\src\main\cpp\uapi"
$target = Join-Path $repoRoot "uapi"

if (-not (Test-Path $target)) {
    throw "Missing target headers: $target"
}

if (Test-Path $linkPath) {
    $item = Get-Item $linkPath -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        Write-Host "uapi junction/symlink already present."
        exit 0
    }
    if ($item.PSIsContainer) {
        Write-Host "uapi directory already present."
        exit 0
    }
    # Plain text leftover from git symlink checkout
    Remove-Item -Force $linkPath
}

cmd /c "mklink /J `"$linkPath`" `"$target`"" | Out-Host
if (-not (Test-Path (Join-Path $linkPath "ksu.h"))) {
    throw "Junction created but ksu.h is still missing."
}
Write-Host "OK: $linkPath -> $target"
