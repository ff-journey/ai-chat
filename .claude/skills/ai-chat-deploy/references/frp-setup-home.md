# Home Machine frpc Setup (First-Time, Windows)

## Prerequisites

- Windows 10/11 with PowerShell
- ECS frps already running on `1.14.109.188:7000`
- Know the `auth.token` set in ECS `frps.toml`
- **Important**: Add ECS IP `1.14.109.188` to Mihomo/Clash DIRECT rule if using TUN proxy, otherwise frp handshake will fail with "session shutdown"

## 1. Install frpc

frpc binary location: `G:\tool\frp\frp_0.68.0_windows_amd64\frpc.exe` (v0.68.0, matches ECS frps)

> **Note**: Windows Defender may quarantine `frpc.exe`. Add `G:\tool\frp` to Defender exclusion list before extracting the zip.

Verify:
```powershell
& 'G:\tool\frp\frp_0.68.0_windows_amd64\frpc.exe' --version
# Expected: 0.68.0
```

## 2. Configure frpc.toml

Config file: `deploy\home\config\frpc.toml`

```toml
serverAddr = "1.14.109.188"
serverPort = 7000
auth.method = "token"
auth.token = "<SAME_SECRET_AS_FRPS>"

[[proxies]]
name = "cnn"
type = "tcp"
localIP = "127.0.0.1"
localPort = 9801
remotePort = 9801

[[proxies]]
name = "vllm"
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

## 4. Verify

Check frpc log for "login to server success" and "start proxy success":
```powershell
Get-Content deploy\home\frpc.log
```

On ECS, verify tunnel ports are listening:
```bash
ssh root@1.14.109.188 "ss -tlnp | grep -E '9801|9901'"
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `session shutdown` on connect | Mihomo/Clash TUN proxy intercepting traffic; add `1.14.109.188` to DIRECT rules |
| frpc.exe not found / quarantined | Add `G:\tool\frp` to Windows Defender exclusion list |
| Connection refused | ECS security group must allow TCP 7000; frps must be running |
| Token mismatch error | `auth.token` in `frpc.toml` must exactly match `frps.toml` |
| Local port not available | CNN (9801) or Medical Service (9901) must be running locally before tunnel works end-to-end |
| stdout/stderr redirect error | PowerShell `Start-Process` cannot redirect both to the same file; use separate `-err.log` |
