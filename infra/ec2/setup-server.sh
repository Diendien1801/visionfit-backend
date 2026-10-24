#!/usr/bin/env bash
# First-time setup of a fresh Ubuntu EC2 instance for docker-compose.prod.yml. Run once as ubuntu:
#   curl -fsSL https://raw.githubusercontent.com/Diendien1801/visionfit-backend/main/infra/ec2/setup-server.sh | bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "== Docker"
sudo apt-get update -q
sudo apt-get install -y -q ca-certificates curl git
if sudo apt-get install -y -q docker.io docker-compose-v2 docker-buildx; then
    echo "   from Ubuntu's own packages"
else
    echo "   from Docker's repository"
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update -q
    sudo apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

echo "== 2 GB swap: five JVMs on 2 GB of RAM need headroom"
if ! sudo swapon --show | grep -q /swapfile; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile > /dev/null
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi

echo "== Security updates install themselves"
sudo apt-get install -y -q unattended-upgrades
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== Code"
if [ ! -d "$HOME/visionfit-backend" ]; then
    git clone -q https://github.com/Diendien1801/visionfit-backend.git "$HOME/visionfit-backend"
fi

cat <<'EOF'

Done. Next:
  1. On the server:  cd ~/visionfit-backend && cp .env.example .env && nano .env   (passwords, JWT secret, tunnel token)
  2. On the server:  ./infra/ec2/deploy.sh   (builds the jars in a JDK container, then starts compose)
EOF
