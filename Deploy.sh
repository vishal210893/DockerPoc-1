#!/usr/bin/env bash
###############################################################################
# Deploy helper:
#   ./Deploy.sh                build skipped; apply manifests + port-forward
#   ./Deploy.sh --build        Steps 1-3b (build + push + patch), then deploy
#   ./Deploy.sh --build-only   Steps 1-3b only; no kubectl apply or port-forward
###############################################################################

set -euo pipefail

# ── colors ────────────────────────────────────────────────────────────────────
GREEN='\e[32m'
NC='\e[0m'

log() { printf "${GREEN}%s${NC}\n" "$*"; }

# Cross-platform in-place sed. macOS (BSD) and Linux (GNU) sed disagree on `-i`
# argument parsing, so we write to a sibling temp file and rename. Pass `-E`
# (or any other sed flags) before the script. Args: <flag1>... <script> <file>
sed_inplace() {
  local file
  for file in "$@"; do :; done   # last arg is the target file
  local tmp="${file}.tmp.$$"
  sed "$@" > "$tmp" && mv "$tmp" "$file"
}

# ── script location ───────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
log "Script running from: ${SCRIPT_DIR}"
echo

# ── config ────────────────────────────────────────────────────────────────────
DOCKER_IMAGE_BASE="vishal210893/dockerpoc-1"

K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/infra/kubernetes/App/Deployment.yaml"
K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/infra/kubernetes/Ingress"
HELM_VALUES_FILE="${SCRIPT_DIR}/infra/helm/dockerpoc-app/values.yaml"

INGRESS_NAMESPACE="ingress-nginx"
INGRESS_SERVICE_NAME="ingress-nginx-controller"

LOCAL_PORT=8005
REMOTE_PORT=80
KUBECTL="kubectl"              # change to your alias if desired
PERFORM_BUILD=false            # default
PERFORM_DEPLOY=true            # default

# ── arg parsing ───────────────────────────────────────────────────────────────
case "${1:-}" in
  --build)
    PERFORM_BUILD=true
    log "--build flag detected ⇒ Steps 1-3 enabled, deploy will follow"
    ;;
  --build-only)
    PERFORM_BUILD=true
    PERFORM_DEPLOY=false
    log "--build-only flag detected ⇒ Steps 1-3 enabled, deploy skipped"
    ;;
  "")
    log "Skipping build (use --build or --build-only to enable Steps 1-3)"
    ;;
  *)
    echo "Unknown flag: ${1}"
    echo "Usage: $0 [--build|--build-only]"
    exit 2
    ;;
esac
echo

# ── sanity checks ─────────────────────────────────────────────────────────────
[[ -f "$K8S_APP_MANIFEST_FILE" ]]     || { echo "ERROR: $K8S_APP_MANIFEST_FILE not found"; exit 1; }
[[ -d "$K8S_INGRESS_MANIFEST_PATH" ]] || { echo "ERROR: $K8S_INGRESS_MANIFEST_PATH not found"; exit 1; }
[[ -f "$HELM_VALUES_FILE" ]]          || { echo "ERROR: $HELM_VALUES_FILE not found"; exit 1; }

# ── optional build (1-3) ──────────────────────────────────────────────────────
if $PERFORM_BUILD; then
  [[ -f "$SCRIPT_DIR/pom.xml" ]] || { echo "ERROR: pom.xml missing"; exit 1; }
  [[ -f "$SCRIPT_DIR/infra/docker/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }

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
  docker build -f "${SCRIPT_DIR}/infra/docker/Dockerfile" -t "$IMAGE" "$SCRIPT_DIR"
  echo
  docker push "$IMAGE"
  log "✓ Image pushed"
  echo

  # 3 ▒▒▒ Patch Deployment image ▒▒▒
  log "========== Step 3: Update Deployment image =========="
  sed_inplace -e "s|image: ${DOCKER_IMAGE_BASE}:.*|image: ${IMAGE}|g" "$K8S_APP_MANIFEST_FILE"
  log "✓ Deployment YAML updated to $IMAGE"
  echo

  # 3b ▒▒▒ Patch Helm chart tag ▒▒▒
  log "========== Step 3b: Update Helm chart values =========="
  TAG_COUNT=$(grep -cE '^[[:space:]]*tag:[[:space:]]*"' "$HELM_VALUES_FILE" || true)
  if [[ "$TAG_COUNT" -eq 1 ]]; then
    sed_inplace -E "s|^([[:space:]]*tag:[[:space:]]*)\".*\"|\1\"${TIMESTAMP}\"|" "$HELM_VALUES_FILE"
    log "✓ Helm values.yaml tag updated to ${TIMESTAMP}"
  else
    log "⚠  Skipped: values.yaml has ${TAG_COUNT} 'tag:' lines (expected exactly 1) — update manually if needed"
  fi
  echo
fi

# ── deploy (4-6) ──────────────────────────────────────────────────────────────
if ! $PERFORM_DEPLOY; then
  log "========== Build-only mode: skipping Steps 4-6 =========="
  exit 0
fi

# 4 ▒▒▒ Ingress ▒▒▒
log "========== Step 4: Apply Ingress =========="
echo

if $KUBECTL -n "$INGRESS_NAMESPACE" get svc "$INGRESS_SERVICE_NAME" &>/dev/null; then
  log "Ingress controller '$INGRESS_SERVICE_NAME' already running in namespace '$INGRESS_NAMESPACE' — skipping apply"
else
  log "Ingress controller not found — applying ingress manifests"
  $KUBECTL apply -f "$K8S_INGRESS_MANIFEST_PATH"
  echo
  sleep 90
fi

# 5 ▒▒▒ App manifests ▒▒▒
log "========== Step 5: Apply Application =========="
echo
$KUBECTL apply -f "$K8S_APP_MANIFEST_FILE"
echo
sleep 30

# 6 ▒▒▒ Port-forward ▒▒▒
log "========== Step 6: Port-forward =========="
log "Forwarding $INGRESS_NAMESPACE/$INGRESS_SERVICE_NAME $REMOTE_PORT → localhost:$LOCAL_PORT"
echo
log "Visit: http://localhost:${LOCAL_PORT}/ingress/dockerpoc/api/v1/version"
echo
log "Swagger URL : http://localhost:${LOCAL_PORT}/ingress/dockerpoc/swagger-ui/index.html"
$KUBECTL -n "$INGRESS_NAMESPACE" port-forward "svc/${INGRESS_SERVICE_NAME}" "${LOCAL_PORT}:${REMOTE_PORT}"


# reaches here only after Ctrl-C
echo
log "Port-forward stopped — all done!"
