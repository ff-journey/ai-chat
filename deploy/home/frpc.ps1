# frpc.ps1 — frpc 客户端启动/停止脚本 (Windows PowerShell)
# 用法: .\frpc.ps1 start  或  .\frpc.ps1 stop
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "stop")]
    [string]$Action
)

$FrpcBin  = "C:\frp\frpc.exe"
$ConfigFile = "$PSScriptRoot\config\frpc.toml"
$PidFile    = "$PSScriptRoot\frpc.pid"
$LogFile    = "$PSScriptRoot\frpc.log"

switch ($Action) {
    "start" {
        if (Test-Path $PidFile) {
            $pid = Get-Content $PidFile
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "frpc is already running (PID: $pid)"
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
        $proc = Start-Process -FilePath $FrpcBin `
            -ArgumentList "-c", $ConfigFile `
            -RedirectStandardOutput $LogFile `
            -RedirectStandardError $LogFile `
            -NoNewWindow -PassThru
        $proc.Id | Set-Content $PidFile
        Write-Host "frpc started (PID: $($proc.Id)), logs: $LogFile"
    }

    "stop" {
        if (-not (Test-Path $PidFile)) {
            Write-Host "PID file not found. frpc may not be running."
            exit 1
        }
        $pid = Get-Content $PidFile
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping frpc (PID: $pid)..."
            Stop-Process -Id $pid -Force
            Remove-Item $PidFile -Force
            Write-Host "frpc stopped."
        } else {
            Write-Host "Process $pid is not running. Cleaning up stale PID file."
            Remove-Item $PidFile -Force
            exit 1
        }
    }
}
