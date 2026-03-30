# ECS First-Time Setup

## 1. Install SDKMAN + JDK (via setup.sh)

Run the project's setup script — it installs SDKMAN and the JDK version declared in `.sdkmanrc` automatically:

```bash
bash ~/ai-chat/deploy/ecs/setup.sh
```

The script is idempotent: re-running it skips already-installed components. After it completes, `java-app.sh` will use SDKMAN to switch to the correct JDK on every start.

To verify:
```bash
source ~/.sdkman/bin/sdkman-init.sh
java -version
```

## 2. Install frps

See `frp-setup-ecs.md` for detailed frps installation, configuration, and security group setup.

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

See `frp-setup-home.md` for frpc installation and configuration.
