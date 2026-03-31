# frpc.ps1 — frpc 客户端启动/停止脚本 (Windows PowerShell)
# 用法: .\frpc.ps1 start  或  .\frpc.ps1 stop
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "stop")]
    [string]$Action
)

$FrpcBin  = "G:\tool\frp\frp_0.68.0_windows_amd64\frpc.exe"
$ConfigFile = "$PSScriptRoot\config\frpc.toml"
$PidFile    = "$PSScriptRoot\frpc.pid"
$LogFile    = "$PSScriptRoot\frpc.log"

switch ($Action) {
    "start" {
        if (Test-Path $PidFile) {
            $procId = Get-Content $PidFile
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "frpc is already running (PID: $procId)"
                exit 1
            } else {
                Write-Host "Stale PID file found, cleaning up..."
                Remove-Item $PidFile -Force
            }
        }
        if (-not (Test-Path $FrpcBin)) {
            Write-Host "frpc binary not found: $FrpcBin"
            exit 1
        }
        Write-Host "Starting frpc..."
        $errLog = "$PSScriptRoot\frpc-err.log"
        $proc = Start-Process -FilePath $FrpcBin `
            -ArgumentList "-c", $ConfigFile `
            -RedirectStandardOutput $LogFile `
            -RedirectStandardError $errLog `
            -NoNewWindow -PassThru
        $proc.Id | Set-Content $PidFile
        Write-Host "frpc started (PID: $($proc.Id)), logs: $LogFile"
    }

    "stop" {
        if (-not (Test-Path $PidFile)) {
            Write-Host "PID file not found. frpc may not be running."
            exit 1
        }
        $procId = Get-Content $PidFile
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping frpc (PID: $pid)..."
            Stop-Process -Id $procId -Force
            Remove-Item $PidFile -Force
            Write-Host "frpc stopped."
        } else {
            Write-Host "Process $procId is not running. Cleaning up stale PID file."
            Remove-Item $PidFile -Force
            exit 1
        }
    }
}
