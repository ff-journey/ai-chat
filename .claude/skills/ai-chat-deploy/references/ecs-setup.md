# ECS First-Time Setup

## 1. Install Java 21

```bash
# Alibaba Dragonwell (recommended for ECS)
sudo apt-get update
sudo apt-get install -y wget
wget https://dragonwell.oss-cn-hangzhou.aliyuncs.com/21/GA/Dragonwell_21_aarch64.tar.gz
# or use apt repo:
sudo apt-get install -y dragonwell-21-jdk

# Verify
java -version
```

## 2. Install frps

```bash
# Download frp release (match version with home frpc)
FRP_VER=0.61.0
wget https://github.com/fatedier/frp/releases/download/v${FRP_VER}/frp_${FRP_VER}_linux_amd64.tar.gz
tar -xzf frp_${FRP_VER}_linux_amd64.tar.gz
sudo cp frp_${FRP_VER}_linux_amd64/frps /usr/local/bin/frps
sudo chmod +x /usr/local/bin/frps
```

## 3. Clone Repository

```bash
cd ~
git clone <repo-url> ai-chat
# or pull latest if already cloned:
cd ~/ai-chat && git pull
```

## 4. Configure .env

```bash
cd ~/ai-chat/deploy/ecs/config
cp .env.example .env
nano .env
# Fill in all required keys
```

## 5. Configure frps.toml

```bash
nano ~/ai-chat/deploy/ecs/config/frps.toml
# Set auth.token to a strong secret string
# Must match frpc.toml on home machine
```

## 6. Open ECS Security Group Ports

In Alibaba Cloud console, ensure inbound rules allow:
- TCP 8080 — Java backend (public)
- TCP 7000 — frps (for frpc connections from home)

## 7. Upload JAR (from local dev machine)

```bash
# Build locally first
cd backend-java/ai-chat-ali && ./gradlew build

# Create target dir on ECS
ssh user@ECS "mkdir -p ~/ai-chat/backend-java/ai-chat-ali/build/libs"

# Upload
scp build/libs/ai-chat-ali.jar user@ECS:~/ai-chat/backend-java/ai-chat-ali/build/libs/
```

## 8. Start Services

```bash
ssh user@ECS "cd ~/ai-chat/deploy/ecs && bash frps.sh start && bash java-app.sh start"
```

## 9. Verify

```bash
ssh user@ECS "ss -tlnp | grep -E '8080|7000'"
curl http://<ECS_PUBLIC_IP>:8080/
```

## Home Machine (Windows) Setup

### Install frpc

1. Download frp release matching ECS frps version
2. Extract `frpc.exe` to a convenient location (e.g. `C:\frp\frpc.exe`)
3. Edit `deploy\home\config\frpc.toml`:
   - Set `serverAddr` to ECS public IP
   - Set `auth.token` to match frps.toml

### Start frpc and CNN

```powershell
.\deploy\home\frpc.ps1 start
.\deploy\home\cnn.ps1 start
```

### Verify tunnel

On ECS: `ss -tlnp | grep 9801` should show port listening after frpc connects.
