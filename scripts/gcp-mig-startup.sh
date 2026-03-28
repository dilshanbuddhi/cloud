#!/bin/bash
# Optional GCE startup script. Stock Ubuntu does not ship JDK 25 — for a reliable MIG, prefer:
#   1) Manually deploy one VM (docs/GCP-VM-DEPLOYMENT.md + scripts/gcp-vm-run.sh).
#   2) Stop VM → Create custom image from its boot disk.
#   3) Instance template: --image=projects/.../global/images/YOUR_IMAGE (no startup), or a tiny
#      script here that only starts PM2 if the image already has the repo under /opt/eca-cloud.
#
# If you still want boot-time clone (slow first health checks), set instance metadata key
#   repo-url=https://github.com/you/Cloud.git
# and extend this script with JDK 25 + Maven + npm install -g pm2 (see GCP-VM-DEPLOYMENT.md).
#
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ca-certificates

APP_DIR="/opt/eca-cloud"
if [[ -x "${APP_DIR}/scripts/gcp-vm-run.sh" ]]; then
  cd "${APP_DIR}"
  chmod +x scripts/gcp-vm-run.sh
  export USE_PM2=1 SKIP_BUILD=1
  bash scripts/gcp-vm-run.sh || true
fi
