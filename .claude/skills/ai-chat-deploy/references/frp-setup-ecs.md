# ECS frps Setup (First-Time)

## Connection Info

- IP: `1.14.109.188`
- SSH: `ssh -i ~/.ssh/id_ed25519 root@1.14.109.188` (GitHub SSH key auth)
- OS: OpenCloudOS 9, x86_64

## 1. Download & Install frps

```bash
ssh root@1.14.109.188

FRP_VER=0.61.0
cd /tmp
wget https://github.com/fatedier/frp/releases/download/v${FRP_VER}/frp_${FRP_VER}_linux_amd64.tar.gz
tar -xzf frp_${FRP_VER}_linux_amd64.tar.gz
sudo cp frp_${FRP_VER}_linux_amd64/frps /usr/local/bin/frps
sudo chmod +x /usr/local/bin/frps
rm -rf frp_${FRP_VER}_linux_amd64 frp_${FRP_VER}_linux_amd64.tar.gz

# Verify
frps --version
```

## 2. Configure frps.toml

The config file lives at `~/ai-chat/deploy/ecs/config/frps.toml` (already in repo).

```bash
nano ~/ai-chat/deploy/ecs/config/frps.toml
```

Expected content:
```toml
bindPort = 7000

auth.method = "token"
auth.token = "<STRONG_SECRET>"
```

Set `auth.token` to a strong random string. This must match `frpc.toml` on the home machine.

## 3. Open ECS Security Group Ports

In Alibaba Cloud console, add inbound rules:

| Port | Protocol | Source | Purpose |
|------|----------|--------|---------|
| 7000 | TCP | 0.0.0.0/0 | frps (tunnel server, frpc connects here) |
| 8080 | TCP | 0.0.0.0/0 | Java backend (public access) |

## 4. Start frps

```bash
cd ~/ai-chat/deploy/ecs
bash frps.sh start
```

Check it's running:
```bash
ss -tlnp | grep 7000
cat ~/ai-chat/deploy/ecs/frps.pid
tail -20 ~/ai-chat/deploy/ecs/frps.log
```

## 5. Verify

After home machine frpc connects, these ports should appear:
```bash
ss -tlnp | grep -E '9801|9901'
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `frps: command not found` | Check `/usr/local/bin/frps` exists and is executable |
| Port 7000 not listening | Check `frps.log` for errors; verify `frps.toml` syntax |
| frpc can't connect | ECS security group must allow TCP 7000 inbound |
| 9801/9901 not appearing | frpc not started on home machine, or token mismatch |
