#!/bin/bash
#
# GCE instance template / MIG startup: install JDK 25, Maven, Git, PM2; clone repo; build; start stack.
#
# Instance metadata (optional keys — Console: VM → Edit → Add metadata):
#   eca-repo-url          — Git clone URL (HTTPS or git@ — SSH needs SSH keys on image)
#   eca-repo-branch       — default: main
#   eureka-url            — Full Eureka URL, e.g. http://203.0.113.5:8761/eureka
#                           If unset, uses this VM's external IP from metadata:
#                           http://<external-ip>:8761/eureka
#   config-server-url     — default: http://127.0.0.1:8888 (platform only). REQUIRED for eca-pm2-ecosystem=services.
#   eca-pm2-ecosystem     — unset or "all" → pm2/ecosystem.config.cjs | "platform" → ecosystem.platform | "services" → ecosystem.services
#   eca-skip-build        — set to "1" if jars already in image (only PM2 start)
#   eca-app-dir           — default: /opt/eca-cloud
#
# Firewall (VPC): TCP 22, 8888, 8761, 8080–8083 (and GCP LB ranges on 8080 for health checks).
#
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

log() { echo "[eca-startup] $*"; }

meta() {
  local key=$1
  curl -fs -H "Metadata-Flavor: Google" \
    "http://metadata.google.internal/computeMetadata/v1/instance/attributes/${key}" 2>/dev/null || true
}

external_ip() {
  curl -fs -H "Metadata-Flavor: Google" \
    "http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip" 2>/dev/null || true
}

install_jdk25() {
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "25'; then
    log "JDK 25 already on PATH."
    return 0
  fi
  local arch="x64"
  case "$(uname -m)" in
    aarch64) arch="aarch64" ;;
    x86_64)  arch="x64" ;;
  esac
  log "Downloading Eclipse Temurin 25 (${arch})..."
  local tmp="/tmp/temurin25.tar.gz"
  curl -fL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/${arch}/jdk/hotspot/normal/eclipse?project=jdk" -o "${tmp}"
  mkdir -p /opt/java
  tar -xzf "${tmp}" -C /opt/java
  local home
  home="$(find /opt/java -maxdepth 1 -type d -name 'jdk-*' | head -1)"
  if [[ -z "${home}" ]]; then
    log "ERROR: could not find extracted JDK under /opt/java"
    exit 1
  fi
  ln -sfn "${home}" /opt/java/current
  export JAVA_HOME=/opt/java/current
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "export JAVA_HOME=${JAVA_HOME}" >/etc/profile.d/eca-java.sh
  echo 'export PATH="${JAVA_HOME}/bin:${PATH}"' >>/etc/profile.d/eca-java.sh
  chmod +x /etc/profile.d/eca-java.sh
  log "JAVA_HOME=${JAVA_HOME}"
}

APP_DIR="$(meta eca-app-dir)"
[[ -z "${APP_DIR}" ]] && APP_DIR="/opt/eca-cloud"
REPO_URL="$(meta eca-repo-url)"
REPO_BRANCH="$(meta eca-repo-branch)"
[[ -z "${REPO_BRANCH}" ]] && REPO_BRANCH="main"
SKIP_BUILD="$(meta eca-skip-build)"
PM2_TIER="$(meta eca-pm2-ecosystem)"

log "apt update & base packages..."
apt-get update -y
apt-get install -y --no-install-recommends curl ca-certificates git maven gnupg

install_jdk25
# shellcheck source=/dev/null
source /etc/profile.d/eca-java.sh 2>/dev/null || true

log "Node.js (PM2)..."
if ! command -v npm >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
npm install -g pm2

EUREKA_URL_META="$(meta eureka-url)"
CONFIG_SERVER_URL_META="$(meta config-server-url)"

if [[ "${PM2_TIER}" == "services" ]]; then
  if [[ -z "${EUREKA_URL_META}" ]]; then
    log "ERROR: eca-pm2-ecosystem=services requires metadata eureka-url (e.g. http://10.x.x.x:8761/eureka)"
    exit 1
  fi
  if [[ -z "${CONFIG_SERVER_URL_META}" ]]; then
    log "ERROR: eca-pm2-ecosystem=services requires metadata config-server-url (e.g. http://10.x.x.x:8888)"
    exit 1
  fi
  EUREKA_URL="${EUREKA_URL_META}"
  CONFIG_SERVER_URL="${CONFIG_SERVER_URL_META}"
else
  if [[ -n "${EUREKA_URL_META}" ]]; then
    EUREKA_URL="${EUREKA_URL_META}"
  else
    EXT_IP="$(external_ip)"
    if [[ -n "${EXT_IP}" ]]; then
      EUREKA_URL="http://${EXT_IP}:8761/eureka"
    else
      EUREKA_URL="http://127.0.0.1:8761/eureka"
      log "WARNING: no external IP; EUREKA_URL fallback ${EUREKA_URL}"
    fi
  fi
  if [[ -n "${CONFIG_SERVER_URL_META}" ]]; then
    CONFIG_SERVER_URL="${CONFIG_SERVER_URL_META}"
  else
    CONFIG_SERVER_URL="http://127.0.0.1:8888"
  fi
fi

mkdir -p "${APP_DIR}" /opt/eca-cloud-logs

if [[ -n "${REPO_URL}" ]]; then
  if [[ -d "${APP_DIR}/.git" ]]; then
    log "Repo exists; git pull..."
    git -C "${APP_DIR}" pull || true
  else
    log "Cloning ${REPO_URL} branch ${REPO_BRANCH}..."
    rm -rf "${APP_DIR}"
    git clone -b "${REPO_BRANCH}" --depth 1 "${REPO_URL}" "${APP_DIR}"
  fi
else
  log "No metadata eca-repo-url — expecting pre-baked app under ${APP_DIR}"
fi

cd "${APP_DIR}" || exit 1
export CONFIG_REPO_PATH="${APP_DIR}/config-repo"
export EUREKA_URL
export CONFIG_SERVER_URL
mkdir -p logs

ENV_FILE="${APP_DIR}/deploy/gcp-vm.env"
mkdir -p "$(dirname "${ENV_FILE}")"
cat >"${ENV_FILE}" <<EOF
CONFIG_SERVER_URL=${CONFIG_SERVER_URL}
EUREKA_URL=${EUREKA_URL}
CONFIG_REPO_PATH=${CONFIG_REPO_PATH}
SPRING_PROFILES_ACTIVE=prod
EOF
log "Wrote ${ENV_FILE}"

if [[ "${SKIP_BUILD}" != "1" ]]; then
  log "Maven build (all modules)..."
  mvn -q clean package -DskipTests
else
  log "eca-skip-build=1 — skipping Maven"
fi

chmod +x scripts/gcp-vm-run.sh 2>/dev/null || true

ECOSYSTEM_FILE="pm2/ecosystem.config.cjs"
case "${PM2_TIER}" in
  platform) ECOSYSTEM_FILE="pm2/ecosystem.platform.config.cjs" ;;
  services) ECOSYSTEM_FILE="pm2/ecosystem.services.config.cjs" ;;
  all | '') ECOSYSTEM_FILE="pm2/ecosystem.config.cjs" ;;
  *)
    log "WARN: unknown eca-pm2-ecosystem='${PM2_TIER}', using all-in-one ecosystem"
    ECOSYSTEM_FILE="pm2/ecosystem.config.cjs"
    ;;
esac

if [[ -f "${APP_DIR}/${ECOSYSTEM_FILE}" ]]; then
  log "Starting PM2 (${ECOSYSTEM_FILE}, tier=${PM2_TIER:-all})..."
  cd "${APP_DIR}"
  pm2 delete all 2>/dev/null || true
  pm2 start "${ECOSYSTEM_FILE}"
  pm2 save
  # systemd unit for root
  env PATH="${PATH}" pm2 startup systemd -u root --hp /root
  log "PM2 started. journal: pm2 logs ; status: pm2 status"
else
  log "No ${ECOSYSTEM_FILE} — run scripts/gcp-vm-run.sh manually"
fi

log "Done. Eureka: ${EUREKA_URL} Config: ${CONFIG_SERVER_URL}"
