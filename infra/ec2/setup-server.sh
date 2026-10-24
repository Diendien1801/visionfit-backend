#!/usr/bin/env bash
# First-time setup of a fresh Ubuntu 24.04 EC2 instance for docker-compose.prod.yml. Run once as ubuntu:
#   curl -fsSL https://raw.githubusercontent.com/Diendien1801/visionfit-backend/main/infra/ec2/setup-server.sh | bash
set -euo pipefail

echo "== Docker (official repository)"
sudo apt-get update -q
sudo apt-get install -y -q ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -q
sudo apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"

echo "== 2 GB swap: five JVMs on 4 GB need headroom while they start"
if ! sudo swapon --show | grep -q /swapfile; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi

echo "== Security updates install themselves"
sudo apt-get install -y -q unattended-upgrades
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== Code"
if [ ! -d "$HOME/visionfit-backend" ]; then
    git clone https://github.com/Diendien1801/visionfit-backend.git "$HOME/visionfit-backend"
fi

cat <<'EOF'

Done. Next:
  1. Log out and back in (so the docker group applies), then: cd ~/visionfit-backend
  2. cp .env.example .env  and fill in every value (random passwords, the JWT secret, the tunnel token)
  3. docker compose -f docker-compose.prod.yml up -d --build
EOF
