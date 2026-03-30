# Home Machine frpc Setup (First-Time, Windows)

## Prerequisites

- Windows 10/11 with PowerShell
- ECS frps already running on `1.14.109.188:7000`
- Know the `auth.token` set in ECS `frps.toml`

## 1. Download & Install frpc

```powershell
# Download frp release (same version as ECS frps)
$FRP_VER = "0.61.0"
$url = "https://github.com/fatedier/frp/releases/download/v${FRP_VER}/frp_${FRP_VER}_windows_amd64.zip"
Invoke-WebRequest -Uri $url -OutFile "$env:TEMP\frp.zip"

# Extract
Expand-Archive -Path "$env:TEMP\frp.zip" -DestinationPath "$env:TEMP\frp" -Force

# Copy frpc.exe to a permanent location
New-Item -ItemType Directory -Path "C:\frp" -Force
Copy-Item "$env:TEMP\frp\frp_${FRP_VER}_windows_amd64\frpc.exe" "C:\frp\frpc.exe"

# Verify
C:\frp\frpc.exe --version
```

## 2. Configure frpc.toml

Edit `deploy\home\config\frpc.toml` in the project repo:

```toml
serverAddr = "1.14.109.188"
serverPort = 7000

auth.method = "token"
auth.token = "<SAME_SECRET_AS_FRPS>"

[[proxies]]
name = "cnn-9801"
type = "tcp"
localIP = "127.0.0.1"
localPort = 9801
remotePort = 9801

[[proxies]]
name = "vllm-9901"
type = "tcp"
localIP = "127.0.0.1"
localPort = 9901
remotePort = 9901
```

Set `auth.token` to the same value as ECS `frps.toml`.

## 3. Start frpc

```powershell
cd G:\code\ai-chat
.\deploy\home\frpc.ps1 start
```

Or manually:
```powershell
C:\frp\frpc.exe -c G:\code\ai-chat\deploy\home\config\frpc.toml
```

## 4. Verify

On the home machine, frpc output should show successful connection. Then on ECS:
```bash
ssh root@1.14.109.188 "ss -tlnp | grep -E '9801|9901'"
```

Both ports should show as listening.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `frpc.exe` not found | Ensure `C:\frp\frpc.exe` exists; or adjust path in `frpc.ps1` |
| Connection refused | ECS security group must allow TCP 7000; frps must be running |
| Token mismatch error | `auth.token` in `frpc.toml` must exactly match `frps.toml` |
| Local port not available | CNN (9801) or vLLM (9901) must be running locally before tunnel works end-to-end |
| Tunnel drops frequently | Check home network stability; frpc auto-reconnects by default |
