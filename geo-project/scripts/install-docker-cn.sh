#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  exec sudo bash "$0" "$@"
fi

. /etc/os-release

if [ "${ID}" != "ubuntu" ]; then
  echo "This installer supports Ubuntu only. Detected: ${ID}" >&2
  exit 1
fi

ARCH="$(dpkg --print-architecture)"
CODENAME="${VERSION_CODENAME}"
DOCKER_MIRROR="${DOCKER_APT_MIRROR:-https://mirrors.tencent.com/docker-ce/linux/ubuntu}"
UBUNTU_MIRROR="${UBUNTU_APT_MIRROR:-https://mirrors.tencent.com/ubuntu}"

echo "Using Ubuntu codename: ${CODENAME}"
echo "Using Docker apt mirror: ${DOCKER_MIRROR}"

sed -i.bak \
  -e "s|http://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
  -e "s|http://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
  -e "s|https://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
  -e "s|https://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
  /etc/apt/sources.list || true

apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
curl -fsSL "${DOCKER_MIRROR}/gpg" | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

cat >/etc/apt/sources.list.d/docker.list <<EOF
deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] ${DOCKER_MIRROR} ${CODENAME} stable
EOF

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

install -m 0755 -d /etc/docker
cat >/etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.m.daocloud.io"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF

systemctl daemon-reload
systemctl enable --now docker
systemctl restart docker

docker --version
docker compose version
