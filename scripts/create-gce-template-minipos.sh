#!/usr/bin/env bash
#
# Create GCE instance template "instance-template-minipos" with startup script + repo metadata
# for scripts/gcp-instance-startup.sh.
#
# Run from repository root on Cloud Shell / Linux (where gcloud + this repo exist), after:
#   gcloud config set project YOUR_PROJECT_ID
#
# If the template name already exists, delete it first:
#   gcloud compute instance-templates delete instance-template-minipos
#
set -euo pipefail

TEMPLATE_NAME="${TEMPLATE_NAME:-instance-template-minipos}"
REGION="${REGION:-asia-south1}"
MACHINE_TYPE="${MACHINE_TYPE:-e2-medium}"
NETWORK_TAGS="${NETWORK_TAGS:-eca-gateway,http-server}"

ECA_REPO_URL="${ECA_REPO_URL:-https://github.com/dilshanbuddhi/cloud.git}"
ECA_REPO_BRANCH="${ECA_REPO_BRANCH:-main}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STARTUP_SCRIPT="${REPO_ROOT}/scripts/gcp-instance-startup.sh"

if [[ ! -f "${STARTUP_SCRIPT}" ]]; then
  echo "ERROR: Startup script not found: ${STARTUP_SCRIPT}"
  exit 1
fi

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: No GCP project. Run: gcloud config set project YOUR_PROJECT_ID"
  exit 1
fi

if gcloud compute instance-templates describe "${TEMPLATE_NAME}" --project="${PROJECT_ID}" &>/dev/null; then
  echo "ERROR: Template '${TEMPLATE_NAME}' already exists. Delete it or set TEMPLATE_NAME:"
  echo "  gcloud compute instance-templates delete ${TEMPLATE_NAME}"
  exit 1
fi

echo "Creating ${TEMPLATE_NAME} (project ${PROJECT_ID}, region ${REGION})..."
gcloud compute instance-templates create "${TEMPLATE_NAME}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --machine-type="${MACHINE_TYPE}" \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --tags="${NETWORK_TAGS}" \
  --scopes=https://www.googleapis.com/auth/cloud-platform \
  --metadata="eca-repo-url=${ECA_REPO_URL},eca-repo-branch=${ECA_REPO_BRANCH}" \
  --metadata-from-file=startup-script="${STARTUP_SCRIPT}"

echo "OK: gcloud compute instance-templates describe ${TEMPLATE_NAME}"
