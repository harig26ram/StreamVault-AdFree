# Restore opencode-preview working setup
# Run from the StreamVault-AdFree project root, or set $BASE.
param(
  [string]$BASE = "C:\Users\harig\OneDrive\Documents\gihub_off\ARIES_off\StreamVault-AdFree",
  [string]$BACKUP = $(Get-ChildItem "$BASE\.opencode-backup-*" -Directory | Sort-Object Name | Select-Object -Last 1 | ForEach-Object { $_.FullName })
)

if (-not $BACKUP -or -not (Test-Path $BACKUP)) {
  Write-Error "Backup not found. Pass -BACKUP <path>."
  exit 1
}

$oc = "$BASE\.opencode"
if (-not (Test-Path $oc)) { New-Item -ItemType Directory -Force -Path $oc | Out-Null }

# Restore plugin (overwrite existing)
Copy-Item "$BACKUP\plugins\opencode-preview" "$oc\plugins\opencode-preview" -Recurse -Force

# Restore project config
Copy-Item "$BACKUP\opencode.json" "$oc\opencode.json" -Force

Write-Host "Restored from: $BACKUP"
Write-Host "Restart the OpenCode desktop app to load it."
