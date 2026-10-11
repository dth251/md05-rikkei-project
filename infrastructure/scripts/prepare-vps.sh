#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" == 0 ]] || { echo 'Initial setup requires root' >&2; exit 1; }
public_key="${1:?Provide the dedicated CI SSH public key file}"
[[ -f "$public_key" ]]
IFS= read -r key < "$public_key"
[[ "$key" == ssh-ed25519\ * ]] || { echo 'Expected an Ed25519 public key' >&2; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git jq openssl nginx certbot python3-certbot-nginx
if ! docker compose version > /dev/null 2>&1; then
    . /etc/os-release
    [[ "$ID" == ubuntu ]] || { echo 'Docker setup currently supports Ubuntu' >&2; exit 1; }
    install -m 0755 -d /etc/apt/keyrings
    curl --fail --silent --show-error https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod 0644 /etc/apt/keyrings/docker.asc
    architecture="$(dpkg --print-architecture)"
    printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' "$architecture" "$VERSION_CODENAME" > /etc/apt/sources.list.d/omnichannel-docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
id deploy > /dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy
install -d -m 0700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
if ! grep -Fqx "$key" /home/deploy/.ssh/authorized_keys; then
    printf '%s\n' "$key" >> /home/deploy/.ssh/authorized_keys
fi
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 0600 /home/deploy/.ssh/authorized_keys
install -d -m 0750 -o deploy -g deploy /opt/omnichannel-ecommerce
echo 'VPS tools and dedicated deploy account ready; existing databases/volumes preserved.'
