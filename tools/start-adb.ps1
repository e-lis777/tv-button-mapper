param([string]$Tv="192.168.0.251:5555",[switch]$Stop)
$ErrorActionPreference="Stop"
$adb="C:/Android/SDK/platform-tools/adb.exe"
function Invoke-Adb { param([string[]]$Arguments) $result=& $adb -s $Tv @Arguments 2>&1; if($LASTEXITCODE -ne 0){throw ($result -join "`n")}; return ($result -join "`n") }
& $adb connect $Tv
if($LASTEXITCODE -ne 0){throw "ADB connection failed"}
$identity=Invoke-Adb -Arguments @("shell","id")
if($identity -notmatch "uid=2000\(shell\)"){throw "Use ordinary ADB shell. Run adb unroot and reconnect first; root mode is not used."}
# Kill only our verified helper PID; closing it automatically releases the remote.
$pidText=& $adb -s $Tv shell cat /data/local/tmp/tvbuttons-bridge.pid 2>$null
if(($pidText -join "").Trim() -match "^([0-9]+)$"){
 $bridgePid=$Matches[1]
 $commandLine=& $adb -s $Tv shell cat "/proc/$bridgePid/cmdline" 2>$null
 if(($commandLine -join "") -match "org.tvbuttons.free.ShellBridge"){Invoke-Adb -Arguments @("shell","kill",$bridgePid)|Out-Null}
}
if($Stop){Write-Host "ADB helper stopped; remote released.";exit}
$apk=Join-Path $PSScriptRoot "../app/build/outputs/apk/debug/app-debug.apk"
$events=Join-Path $PSScriptRoot "tvbuttons-events"
if(!(Test-Path -LiteralPath $apk) -or !(Test-Path -LiteralPath $events)){throw "Build APK and native event helper first."}
Invoke-Adb -Arguments @("install","-r",$apk)|Write-Host
Invoke-Adb -Arguments @("push",$apk,"/data/local/tmp/tvbuttons-bridge.apk")|Out-Null
Invoke-Adb -Arguments @("push",$events,"/data/local/tmp/tvbuttons-events")|Out-Null
Invoke-Adb -Arguments @("shell","chmod","700","/data/local/tmp/tvbuttons-events")|Out-Null
$uidLine=Invoke-Adb -Arguments @("shell","cmd","package","list","packages","-U","org.tvbuttons.free")
if($uidLine -notmatch "uid:([0-9]+)"){throw "Application UID not found"}
$appUid=$Matches[1]
$start="setsid env CLASSPATH=/data/local/tmp/tvbuttons-bridge.apk app_process /system/bin org.tvbuttons.free.ShellBridge $appUid >/data/local/tmp/tvbuttons-bridge.log 2>&1 </dev/null &"
Invoke-Adb -Arguments @("shell",$start)|Out-Null
Invoke-Adb -Arguments @("shell","am","start","-n","org.tvbuttons.free/.MainActivity","--ez","connect_adb","true")|Write-Host
for($attempt=0;$attempt -lt 10;$attempt++){Start-Sleep -Seconds 1;$log=Invoke-Adb -Arguments @("shell","cat","/data/local/tmp/tvbuttons-bridge.log");if($log -match "READY uid=2000"){break}}
if($log -notmatch "READY uid=2000"){throw "Helper did not start: $log"}
Write-Host $log
Write-Host "Helper runs on TV without the computer. Repeat this script after a TV reboot."
