# cnn.ps1 — Python CNN 推理服务启动/停止脚本 (Windows PowerShell)
# 用法: .\cnn.ps1 start  或  .\cnn.ps1 stop
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "stop")]
    [string]$Action
)

$PythonBin = "python"
$AppDir    = (Resolve-Path "$PSScriptRoot\..\..\backend-python\model_interface").Path
$PidFile   = "$PSScriptRoot\cnn.pid"
$LogFile   = "$PSScriptRoot\cnn.log"

switch ($Action) {
    "start" {
        if (Test-Path $PidFile) {
            $pid = Get-Content $PidFile
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "CNN service is already running (PID: $pid)"
                exit 1
            } else {
                Write-Host "Stale PID file found, cleaning up..."
                Remove-Item $PidFile -Force
            }
        }
        if (-not (Test-Path "$AppDir\main.py")) {
            Write-Host "main.py not found in: $AppDir"
            exit 1
        }
        Write-Host "Starting CNN service..."
        $proc = Start-Process -FilePath $PythonBin `
            -ArgumentList "main.py" `
            -WorkingDirectory $AppDir `
            -RedirectStandardOutput $LogFile `
            -RedirectStandardError $LogFile `
            -NoNewWindow -PassThru
        $proc.Id | Set-Content $PidFile
        Write-Host "CNN service started (PID: $($proc.Id)), logs: $LogFile"
    }

    "stop" {
        if (-not (Test-Path $PidFile)) {
            Write-Host "PID file not found. CNN service may not be running."
            exit 1
        }
        $pid = Get-Content $PidFile
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping CNN service (PID: $pid)..."
            Stop-Process -Id $pid -Force
            Remove-Item $PidFile -Force
            Write-Host "CNN service stopped."
        } else {
            Write-Host "Process $pid is not running. Cleaning up stale PID file."
            Remove-Item $PidFile -Force
            exit 1
        }
    }
}
