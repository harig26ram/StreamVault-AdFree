# Auto-handle Android emulator modals during verification.
# Usage: .\auto-modal.ps1
# Detects common dialogs and acts: permission "Allow" -> tap; "Deny" -> tap Allow anyway for continuity.

$adb = "C:\Users\harig\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$tmp = "$env:TEMP\ui.xml"

function Dump-UI {
    & $adb shell uiautomator dump /sdcard/ui.xml 2>$null
    & $adb pull /sdcard/ui.xml $tmp 2>$null | Out-Null
    return Get-Content $tmp -Raw
}

function Tap-Bounds($bounds) {
    # bounds format: [x1,y1][x2,y2]
    if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x1,$y1,$x2,$y2 = [int]$Matches[1],[int]$Matches[2],[int]$Matches[3],[int]$Matches[4]
        $cx = [int](($x1+$x2)/2); $cy = [int](($y1+$y2)/2)
        & $adb shell input tap $cx $cy
    }
}

$handled = 0
$package = ""
for ($i=0; $i -lt 8; $i++) {
    $xml = Dump-UI
    if ($xml -match 'package="([^"]+)"') { $package = $Matches[1] }
    if ($package -ne "com.google.android.permissioncontroller" -and $package -ne "com.android.permissioncontroller") {
        Write-Host "No modal at pass $i (package=$package)"
        break
    }
    # find Allow / While using app / Yes buttons
    $targets = @("Allow","While using","Allow anyway","Yes")
    $found = $false
    foreach ($t in $targets) {
        if ($xml -match "text=`"$t`"[^>]*bounds=`"([^`"]+)`"") {
            Write-Host "Tapping '$t' at $($Matches[1])"
            Tap-Bounds $Matches[1]
            $found = $true; $handled++; break
        }
    }
    if (-not $found) {
        # fallback: tap last button node (usually positive action)
        $btns = [regex]::Matches($xml, 'bounds="(\[[^"]+\])"')
        if ($btns.Count -gt 0) {
            $last = $btns[$btns.Count-1].Groups[1].Value
            Write-Host "Fallback tap on $last"
            Tap-Bounds $last
            $handled++
        }
    }
    Start-Sleep -Seconds 2
}
Write-Host "Modals handled: $handled"
