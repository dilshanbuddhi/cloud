#!/usr/bin/env bash
# Run all ECA Cloud services on a Linux VM (GCP SSH terminal, Ubuntu/Debian).
#
# මට ඔබේ GCP VM එකට SSH කරන්න බැහැ — මේ script එක VM එකේ terminal එකේ run කරන්න:
#   chmod +x scripts/gcp-vm-run.sh
#   ./scripts/gcp-vm-run.sh
#
# Optional: copy deploy/gcp-vm.env.example → deploy/gcp-vm.env, edit, then:
#   set -a && source deploy/gcp-vm.env && set +a && ./scripts/gcp-vm-run.sh
#
# Env:
#   SKIP_BUILD=1     — skip mvn package (jars already built)
#   USE_PM2=1        — use PM2 if installed (npm i -g pm2)

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
mkdir -p logs

export CONFIG_SERVER_URL="${CONFIG_SERVER_URL:-http://127.0.0.1:8888}"
export EUREKA_URL="${EUREKA_URL:-http://127.0.0.1:8761/eureka}"
export CONFIG_REPO_PATH="${CONFIG_REPO_PATH:-}"

if [[ -f "$REPO/deploy/gcp-vm.env" ]]; then
  echo "Loading $REPO/deploy/gcp-vm.env"
  set -a
  # shellcheck disable=SC1091
  source "$REPO/deploy/gcp-vm.env"
  set +a
fi

wait_http() {
  local url=$1
  local name=$2
  local max="${3:-90}"
  local n=0
  echo "Waiting for $name ($url) ..."
  while ! curl -sf "$url" >/dev/null 2>&1; do
    sleep 2
    n=$((n + 1))
    if (( n > max )); then
      echo "ERROR: timeout waiting for $name"
      exit 1
    fi
  done
  echo "OK: $name"
}

java_ok() {
  if ! command -v java &>/dev/null; then
    echo "ERROR: java not found. Install JDK 25 on the VM."
    exit 1
  fi
  java -version 2>&1 | head -1
}

if [[ "${USE_PM2:-0}" == "1" ]] && command -v pm2 &>/dev/null; then
  java_ok
  if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
    command -v mvn &>/dev/null || { echo "ERROR: mvn not found"; exit 1; }
    bash "$REPO/scripts/build-all.sh"
  fi
  pm2 delete all 2>/dev/null || true
  pm2 start "$REPO/pm2/ecosystem.config.cjs"
  pm2 save 2>/dev/null || true
  echo "Started with PM2. Check: pm2 status"
  echo "UI: http://$(curl -s -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip 2>/dev/null || hostname -I | awk '{print $1}'):8080/"
  exit 0
fi

java_ok
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  command -v mvn &>/dev/null || { echo "ERROR: mvn not found. sudo apt install maven OR use SDKMAN."; exit 1; }
  bash "$REPO/scripts/build-all.sh"
fi

JAR_CONFIG="$REPO/platform/config-server/target/config-server-0.1.0-SNAPSHOT.jar"
JAR_EUREKA="$REPO/platform/eureka-server/target/eureka-server-0.1.0-SNAPSHOT.jar"
JAR_GW="$REPO/platform/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar"
JAR_USER="$REPO/services/user-service/target/user-service-0.1.0-SNAPSHOT.jar"
JAR_PROD="$REPO/services/product-service/target/product-service-0.1.0-SNAPSHOT.jar"
JAR_ORD="$REPO/services/order-service/target/order-service-0.1.0-SNAPSHOT.jar"

for j in "$JAR_CONFIG" "$JAR_EUREKA" "$JAR_GW" "$JAR_USER" "$JAR_PROD" "$JAR_ORD"; do
  [[ -f "$j" ]] || { echo "ERROR: missing $j — build first."; exit 1; }
done

stop_eca() {
  for pat in config-server-0.1.0 eureka-server-0.1.0 api-gateway-0.1.0 user-service-0.1.0 product-service-0.1.0 order-service-0.1.0; do
    pkill -f "$pat-SNAPSHOT.jar" 2>/dev/null || true
  done
}

if [[ "${STOP_ONLY:-0}" == "1" ]]; then
  stop_eca
  echo "Stopped ECA jars."
  exit 0
fi

stop_eca
sleep 2

echo "Starting config-server..."
cd "$REPO/platform/config-server"
nohup java -jar "$JAR_CONFIG" >>"$REPO/logs/config-server.log" 2>&1 &
wait_http "http://127.0.0.1:8888/actuator/health" "config-server" 60

echo "Starting eureka-server..."
cd "$REPO/platform/eureka-server"
nohup env CONFIG_SERVER_URL="$CONFIG_SERVER_URL" java -jar "$JAR_EUREKA" >>"$REPO/logs/eureka-server.log" 2>&1 &
wait_http "http://127.0.0.1:8761/actuator/health" "eureka-server" 90

echo "Starting microservices + api-gateway..."
cd "$REPO/services/user-service"
nohup env CONFIG_SERVER_URL="$CONFIG_SERVER_URL" EUREKA_URL="$EUREKA_URL" java -jar "$JAR_USER" >>"$REPO/logs/user-service.log" 2>&1 &

cd "$REPO/services/product-service"
nohup env CONFIG_SERVER_URL="$CONFIG_SERVER_URL" EUREKA_URL="$EUREKA_URL" \
  STORAGE_PROVIDER="${STORAGE_PROVIDER:-gcs}" \
  java -jar "$JAR_PROD" >>"$REPO/logs/product-service.log" 2>&1 &

cd "$REPO/services/order-service"
nohup env CONFIG_SERVER_URL="$CONFIG_SERVER_URL" EUREKA_URL="$EUREKA_URL" java -jar "$JAR_ORD" >>"$REPO/logs/order-service.log" 2>&1 &

# Let services register in Eureka before the gateway starts
sleep 25

cd "$REPO/platform/api-gateway"
nohup env CONFIG_SERVER_URL="$CONFIG_SERVER_URL" EUREKA_URL="$EUREKA_URL" java -jar "$JAR_GW" >>"$REPO/logs/api-gateway.log" 2>&1 &

wait_http "http://127.0.0.1:8080/actuator/health" "api-gateway" 120

echo ""
echo "========== Done =========="
echo "Logs: $REPO/logs/*.log"
echo "Health:"
echo "  curl -s http://127.0.0.1:8080/actuator/health"
echo "Open in browser (use your VM external IP):"
echo "  http://<VM_IP>:8080/"
echo "Stop all:  STOP_ONLY=1 ./scripts/gcp-vm-run.sh"
echo "PM2 mode:  USE_PM2=1 ./scripts/gcp-vm-run.sh   (needs: npm i -g pm2)"
