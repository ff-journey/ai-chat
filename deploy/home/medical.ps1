# medical.ps1 — Medical Diagnosis Service 启动/停止脚本 (Windows PowerShell)
# 用法: .\medical.ps1 start  或  .\medical.ps1 stop
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "stop")]
    [string]$Action
)

$PythonBin = "G:\tool\conda\py310\envs\aienv\python.exe"
$AppDir    = (Resolve-Path "$PSScriptRoot\..\..\backend-python\medical_service").Path
$PidFile   = "$PSScriptRoot\medical.pid"
$LogFile   = "$PSScriptRoot\medical.log"
$ErrLog    = "$PSScriptRoot\medical-err.log"

switch ($Action) {
    "start" {
        if (Test-Path $PidFile) {
            $procId = Get-Content $PidFile
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "Medical service is already running (PID: $procId)"
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
        Write-Host "Starting Medical Diagnosis Service..."
        $proc = Start-Process -FilePath $PythonBin `
            -ArgumentList "main.py" `
            -WorkingDirectory $AppDir `
            -RedirectStandardOutput $LogFile `
            -RedirectStandardError $ErrLog `
            -NoNewWindow -PassThru
        $proc.Id | Set-Content $PidFile
        Write-Host "Medical service started (PID: $($proc.Id)), logs: $LogFile"
    }

    "stop" {
        if (-not (Test-Path $PidFile)) {
            Write-Host "PID file not found. Medical service may not be running."
            exit 1
        }
        $procId = Get-Content $PidFile
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "Stopping Medical service (PID: $pid)..."
            Stop-Process -Id $procId -Force
            Remove-Item $PidFile -Force
            Write-Host "Medical service stopped."
        } else {
            Write-Host "Process $procId is not running. Cleaning up stale PID file."
            Remove-Item $PidFile -Force
            exit 1
        }
    }
}
