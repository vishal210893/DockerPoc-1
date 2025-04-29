#!/usr/bin/env bash
###############################################################################
# Deploy helper: optional build ­→ push → apply manifests → port-forward
###############################################################################

set -euo pipefail

# ── colors ────────────────────────────────────────────────────────────────────
GREEN='\e[32m'
NC='\e[0m'

log() { printf "${GREEN}%s${NC}\n" "$*"; }

# ── script location ───────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
log "Script running from: ${SCRIPT_DIR}"
echo

# ── config ────────────────────────────────────────────────────────────────────
DOCKER_IMAGE_BASE="vishal210893/dockerpoc-1"

K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/K8s_Yaml/App/Deployment.yaml"
K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/K8s_Yaml/Ingress"

INGRESS_NAMESPACE="ingress-nginx"
INGRESS_SERVICE_NAME="ingress-nginx-controller"

LOCAL_PORT=8080
REMOTE_PORT=80
KUBECTL="kubectl"              # change to your alias if desired
PERFORM_BUILD=false            # default

# ── arg parsing ───────────────────────────────────────────────────────────────
if [[ ${1:-} == "--build" ]]; then
  PERFORM_BUILD=true
  log "--build flag detected ⇒ Steps 1-3 enabled"
else
  log "Skipping build (use --build to enable Steps 1-3)"
fi
echo

# ── sanity checks ─────────────────────────────────────────────────────────────
[[ -f "$K8S_APP_MANIFEST_FILE" ]]     || { echo "ERROR: $K8S_APP_MANIFEST_FILE not found"; exit 1; }
[[ -d "$K8S_INGRESS_MANIFEST_PATH" ]] || { echo "ERROR: $K8S_INGRESS_MANIFEST_PATH not found"; exit 1; }

# ── optional build (1-3) ──────────────────────────────────────────────────────
if $PERFORM_BUILD; then
  [[ -f "$SCRIPT_DIR/pom.xml" ]] || { echo "ERROR: pom.xml missing"; exit 1; }
  [[ -f "$SCRIPT_DIR/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }

  # 1 ▒▒▒ Maven build ▒▒▒
  log "========== Step 1: Maven build =========="
  echo
  ( cd "$SCRIPT_DIR" && mvn -q install )
  echo
  log "✓ Maven build complete"
  echo

  # 2 ▒▒▒ Docker build & push ▒▒▒
  log "========== Step 2: Docker build & push =========="
  TIMESTAMP=$(date +%Y%m%d-%H%M%S)
  IMAGE="${DOCKER_IMAGE_BASE}:${TIMESTAMP}"
  log "Building image → $IMAGE"
  echo
  docker build -t "$IMAGE" "$SCRIPT_DIR"
  echo
  docker push "$IMAGE"
  log "✓ Image pushed"
  echo

  # 3 ▒▒▒ Patch Deployment image ▒▒▒
  log "========== Step 3: Update Deployment image =========="
  sed -i'' -e "s|image: ${DOCKER_IMAGE_BASE}:.*|image: ${IMAGE}|g" "$K8S_APP_MANIFEST_FILE"
  log "✓ Deployment YAML updated to $IMAGE"
  echo
fi

# ── deploy (4-6) ──────────────────────────────────────────────────────────────
# 4 ▒▒▒ Ingress ▒▒▒
log "========== Step 4: Apply Ingress =========="
echo
$KUBECTL apply -f "$K8S_INGRESS_MANIFEST_PATH"
echo
sleep 30

# 5 ▒▒▒ App manifests ▒▒▒
log "========== Step 5: Apply Application =========="
echo
$KUBECTL apply -f "$K8S_APP_MANIFEST_FILE"
echo
sleep 10

# 6 ▒▒▒ Port-forward ▒▒▒
log "========== Step 6: Port-forward =========="
log "Forwarding $INGRESS_NAMESPACE/$INGRESS_SERVICE_NAME $REMOTE_PORT → localhost:$LOCAL_PORT"
echo
log "Visit: http://localhost:${LOCAL_PORT}/dockerpoc1/api/v1/version"
$KUBECTL -n "$INGRESS_NAMESPACE" port-forward "svc/${INGRESS_SERVICE_NAME}" "${LOCAL_PORT}:${REMOTE_PORT}"


# reaches here only after Ctrl-C
echo
log "Port-forward stopped — all done!"
