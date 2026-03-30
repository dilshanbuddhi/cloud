#!/usr/bin/env bash
#
# GCP: HTTP health check + firewall for LB, then optional MIG + external HTTP load balancer.
#
# Health check hits API Gateway: GET :8080/actuator/health (same port as Mini POS UI).
#
# Prereq: gcloud auth login && gcloud config set project ...
#   gcloud services enable compute.googleapis.com
#
# Recommended: build a custom image from a VM where ./scripts/gcp-vm-run.sh already works,
# then set GOLDEN_IMAGE and run with CREATE_LB=1. See docs/GCP-VM-DEPLOYMENT.md (Load balancer section).
#
set -euo pipefail

# --- edit these (or leave PROJECT_ID empty to use: gcloud config get-value project) ---
PROJECT_ID="${PROJECT_ID:-}"
REGION="asia-south1"
ZONE="${REGION}-a"
NETWORK="default"
MIG_NAME="eca-mig"
TEMPLATE_NAME="eca-gateway-template"
BACKEND_SERVICE="eca-gateway-be"
URL_MAP_NAME="eca-url-map"
TARGET_PROXY="eca-http-proxy"
FORWARDING_RULE="eca-http-rule"
HEALTH_CHECK="eca-gateway-hc"
FIREWALL_HC="eca-allow-lb-health"

# When CREATE_LB=1, set before running, e.g.:
#   export GOLDEN_IMAGE="projects/buddhi-cloud/global/images/eca-cloud-v1"
# Do not assign GOLDEN_IMAGE inside this script — it would clear your export.
GOLDEN_IMAGE="${GOLDEN_IMAGE:-}"

# Set CREATE_LB=1 to create template (needs GOLDEN_IMAGE), MIG, backend service, HTTP LB.
CREATE_LB="${CREATE_LB:-0}"

if [[ -z "${PROJECT_ID}" ]]; then
  PROJECT_ID="$(gcloud config get-value project 2>/dev/null | tr -d '\n' || true)"
fi
if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: No GCP project. Run: gcloud config set project buddhi-cloud"
  exit 1
fi
gcloud config set project "${PROJECT_ID}"

echo "=== 1) Firewall: GCP health checks + LB → backends on 8080 ==="
gcloud compute firewall-rules create "${FIREWALL_HC}" \
  --network="${NETWORK}" \
  --action=ALLOW \
  --direction=INGRESS \
  --rules=tcp:8080 \
  --source-ranges="130.211.0.0/22,35.191.0.0/16" \
  --target-tags=eca-gateway \
  --description="HTTP(S) LB + health probes to gateway :8080" \
  2>/dev/null || echo "(rule may already exist)"

echo "=== 2) HTTP health check (GET /actuator/health on 8080) ==="
gcloud compute health-checks create http "${HEALTH_CHECK}" \
  --port=8080 \
  --request-path=/actuator/health \
  --check-interval=10s \
  --timeout=5s \
  --healthy-threshold=2 \
  --unhealthy-threshold=3 \
  2>/dev/null || echo "(health check may already exist)"

if [[ "${CREATE_LB}" != "1" ]]; then
  echo ""
  echo "CREATE_LB is not 1 — skipping template/MIG/LB. To continue:"
  echo "  1) Create custom image from a VM with the app working (see docs/GCP-VM-DEPLOYMENT.md)."
  echo "  2) export GOLDEN_IMAGE='projects/${PROJECT_ID}/global/images/YOUR_IMAGE'"
  echo "  3) CREATE_LB=1 ./scripts/gcp-mig-lb-setup.sh"
  echo ""
  echo "Manual gcloud (replace YOUR_IMAGE_NAME with your custom image name):"
  echo "  gcloud compute instance-templates create ${TEMPLATE_NAME} --region=${REGION} \\"
  echo "    --machine-type=e2-medium --image=projects/${PROJECT_ID}/global/images/YOUR_IMAGE_NAME \\"
  echo "    --tags=eca-gateway,http-server --scopes=https://www.googleapis.com/auth/cloud-platform"
  echo "  gcloud compute instance-groups managed create ${MIG_NAME} --zone=${ZONE} --template=${TEMPLATE_NAME} --size=2"
  echo "  gcloud compute instance-groups managed set-named-ports ${MIG_NAME} --zone=${ZONE} --named-ports=http:8080"
  echo "  gcloud compute backend-services create ${BACKEND_SERVICE} --global --protocol=HTTP \\"
  echo "    --health-checks=${HEALTH_CHECK} --port-name=http"
  echo "  gcloud compute backend-services add-backend ${BACKEND_SERVICE} --global \\"
  echo "    --instance-group=${MIG_NAME} --instance-group-zone=${ZONE} \\"
  echo "    --balancing-mode=UTILIZATION --max-utilization=0.8"
  echo "  gcloud compute url-maps create ${URL_MAP_NAME} --default-service=${BACKEND_SERVICE}"
  echo "  gcloud compute target-http-proxies create ${TARGET_PROXY} --url-map=${URL_MAP_NAME}"
  echo "  gcloud compute forwarding-rules create ${FORWARDING_RULE} --global \\"
  echo "    --target-http-proxy=${TARGET_PROXY} --ports=80"
  exit 0
fi

if [[ -z "${GOLDEN_IMAGE}" ]]; then
  echo "ERROR: export GOLDEN_IMAGE before CREATE_LB=1, e.g.:"
  echo "  export GOLDEN_IMAGE=\"projects/${PROJECT_ID}/global/images/eca-cloud-v1\""
  echo "(Create that image first from your VM disk — see docs/GCP-VM-DEPLOYMENT.md.)"
  exit 1
fi

IMAGE_BASENAME="${GOLDEN_IMAGE##*/images/}"
IMAGE_BASENAME="${IMAGE_BASENAME%%/*}"
if ! gcloud compute images describe "${IMAGE_BASENAME}" --project="${PROJECT_ID}" &>/dev/null; then
  echo "WARNING: no image named '${IMAGE_BASENAME}' in project ${PROJECT_ID} (from GOLDEN_IMAGE)."
  echo "List: gcloud compute images list --project=${PROJECT_ID}"
  echo "Create from this VM's disk (after stop): gcloud compute images create ${IMAGE_BASENAME} --source-disk=DISK_NAME --source-disk-zone=${ZONE}"
fi

echo "=== 3) Instance template (golden image) ==="
gcloud compute instance-templates create "${TEMPLATE_NAME}" \
  --region="${REGION}" \
  --machine-type=e2-medium \
  --image="${GOLDEN_IMAGE}" \
  --tags=eca-gateway,http-server \
  --scopes=https://www.googleapis.com/auth/cloud-platform \
  2>/dev/null || echo "(template may already exist)"

echo "=== 4) Managed instance group ==="
gcloud compute instance-groups managed create "${MIG_NAME}" \
  --zone="${ZONE}" \
  --template="${TEMPLATE_NAME}" \
  --size=2 \
  2>/dev/null || echo "(MIG may already exist)"

gcloud compute instance-groups managed set-named-ports "${MIG_NAME}" \
  --zone="${ZONE}" \
  --named-ports=http:8080

echo "=== 5) Backend service + MIG ==="
gcloud compute backend-services create "${BACKEND_SERVICE}" \
  --global \
  --protocol=HTTP \
  --health-checks="${HEALTH_CHECK}" \
  --port-name=http \
  2>/dev/null || echo "(backend service may already exist)"

gcloud compute backend-services add-backend "${BACKEND_SERVICE}" \
  --global \
  --instance-group="${MIG_NAME}" \
  --instance-group-zone="${ZONE}" \
  --balancing-mode=UTILIZATION \
  --max-utilization=0.8 \
  2>/dev/null || echo "(backend already attached?)"

echo "=== 6) URL map, HTTP proxy, forwarding rule (port 80) ==="
gcloud compute url-maps create "${URL_MAP_NAME}" \
  --default-service="${BACKEND_SERVICE}" \
  2>/dev/null || echo "(url map may exist)"

gcloud compute target-http-proxies create "${TARGET_PROXY}" \
  --url-map="${URL_MAP_NAME}" \
  2>/dev/null || echo "(proxy may exist)"

gcloud compute forwarding-rules create "${FORWARDING_RULE}" \
  --global \
  --target-http-proxy="${TARGET_PROXY}" \
  --ports=80 \
  2>/dev/null || echo "(forwarding rule may exist)"

echo ""
echo "Load balancer IP:"
gcloud compute forwarding-rules describe "${FORWARDING_RULE}" --global --format='get(IPAddress)' || true
echo ""
echo "Smoke: curl -s http://<LB_IP>/actuator/health"
echo "UI:    http://<LB_IP>/"
echo ""
echo "Gateway UI uses relative /auth, /products URLs — works behind LB on port 80."
echo "Standalone frontend/index.html: use same-origin API (empty base) when not on dev port 3000."
