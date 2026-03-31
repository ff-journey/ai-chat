# cnn.ps1 — Python CNN 推理服务启动/停止脚本 (Windows PowerShell)
# 用法: .\cnn.ps1 start  或  .\cnn.ps1 stop
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "stop")]
    [string]$Action
)

$PythonBin = "G:\tool\conda\py310\envs\aienv\python.exe"
$AppDir    = (Resolve-Path "$PSScriptRoot\..\..\backend-python\model_interface").Path
$PidFile   = "$PSScriptRoot\cnn.pid"
$LogFile   = "$PSScriptRoot\cnn.log"
$ErrLog    = "$PSScriptRoot\cnn-err.log"

switch ($Action) {
    "start" {
        if (Test-Path $PidFile) {
            $procId = Get-Content $PidFile
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "CNN service is already running (PID: $procId)"
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
            -RedirectStandardError $ErrLog `
            -NoNewWindow -PassThru
        $proc.Id | Set-Content $PidFile
        Write-Host "CNN service started (PID: $($proc.Id)), logs: $LogFile"
    }

    "stop" {
        if (-not (Test-Path $PidFile)) {
            Write-Host "PID file not found. CNN service may not be running."
            exit 1
        }
        $procId = Get-Content $PidFile
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping CNN service (PID: $procId)..."
            Stop-Process -Id $procId -Force
            Remove-Item $PidFile -Force
            Write-Host "CNN service stopped."
        } else {
            Write-Host "Process $procId is not running. Cleaning up stale PID file."
            Remove-Item $PidFile -Force
            exit 1
        }
    }
}
