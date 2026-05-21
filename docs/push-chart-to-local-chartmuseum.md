# Push a local Helm chart to a local ChartMuseum

A 5-minute local-dev setup for pushing Helm charts to a private, basic-auth-protected ChartMuseum running on your Mac. Used for testing KubeVela's HTTPS chart-fetch auth path (`pkg/cue/cuex/providers/helm/helm.go:fetchURLChart`) without signing up for Artifactory or JFrog Cloud.

## What this gives you

- ChartMuseum on `http://localhost:8090` with HTTP Basic auth (`test-user` / `test-pass`).
- Anonymous `GET` returns `401`. That's the failure case the controller exercises.
- Authenticated `GET` returns the chart `.tgz`. That's the positive case once the controller is given a Secret reference.
- Chart storage is inside the container (no host volume), so the data is wiped when the container is removed. Fine for local testing; do not use for anything persistent.

## Prerequisites

- Docker Desktop or Rancher Desktop running on macOS.
- A packaged chart `.tgz` somewhere on disk. Example used below: `~/Documents/DockerPoc-1/dist/dockerpoc-app-5.0.1.tgz`.
- Port `8090` free on the host. If your local controller binds `:8080` for metrics, pick any other free port.

## Step 1 — Start ChartMuseum

Runs as root inside the container so it can create `/charts/` without volume permission errors.

```bash
docker rm -f chartmuseum 2>/dev/null

docker run -d \
  --name chartmuseum \
  --user 0:0 \
  -p 8090:8080 \
  -e STORAGE=local \
  -e STORAGE_LOCAL_ROOTDIR=/charts \
  -e BASIC_AUTH_USER=test-user \
  -e BASIC_AUTH_PASS=test-pass \
  -e AUTH_ANONYMOUS_GET=false \
  ghcr.io/helm/chartmuseum:v0.16.2

# Wait a moment for the process to bind the port
sleep 3
curl -s http://localhost:8090/health && echo
# Expected: {"healthy":true}
```

If `curl` returns nothing or 404, something on the host is already bound to 8090. Check with:

```bash
lsof -nP -iTCP:8090 -sTCP:LISTEN
```

## Step 2 — Push the chart

```bash
CHART=/Users/viskumar/Documents/DockerPoc-1/dist/dockerpoc-app-5.0.1.tgz

curl -i -u test-user:test-pass \
     --data-binary "@${CHART}" \
     http://localhost:8090/api/charts
# Expected: HTTP/1.1 201 Created
#           {"saved":true}
```

If you get `500 {"error":"... permission denied"}`, the container is not running as root. Re-run Step 1 with `--user 0:0` (already in the command above).

## Step 3 — Verify the auth gate

Anonymous request — must be rejected:

```bash
curl -i http://localhost:8090/charts/dockerpoc-app-5.0.1.tgz | head -5
# Expected:
#   HTTP/1.1 401 Unauthorized
#   WWW-Authenticate: Basic realm="ChartMuseum"
```

Authenticated request — must succeed:

```bash
curl -fSL -u test-user:test-pass \
     -o /tmp/check.tgz \
     http://localhost:8090/charts/dockerpoc-app-5.0.1.tgz
file /tmp/check.tgz
# Expected:
#   /tmp/check.tgz: gzip compressed data
```

If both behave as expected, ChartMuseum is ready and behaving like a real private Helm repo.

## Useful ChartMuseum endpoints

| Method | Path                            | Auth required | Purpose                                  |
|--------|---------------------------------|---------------|------------------------------------------|
| GET    | `/health`                       | no            | Liveness probe                           |
| GET    | `/index.yaml`                   | yes           | Standard Helm repo index                 |
| POST   | `/api/charts`                   | yes           | Upload a chart (`--data-binary @file.tgz`) |
| GET    | `/api/charts`                   | yes           | List all charts                          |
| GET    | `/api/charts/<name>`            | yes           | List versions of one chart               |
| GET    | `/api/charts/<name>/<version>`  | yes           | Get chart metadata                       |
| GET    | `/charts/<name>-<version>.tgz`  | yes           | Download chart tarball                   |
| DELETE | `/api/charts/<name>/<version>`  | yes           | Remove a single version                  |

## Common errors and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `Conflict. The container name "/chartmuseum" is already in use` | Container from a previous run still there | `docker rm -f chartmuseum` |
| Push returns `500 {"error":"open /charts/...: permission denied"}` | Container UID can't write to the storage dir | Add `--user 0:0` to `docker run` |
| Push returns `500 {"error":"mkdir /charts: permission denied"}` | Same as above — applies when `/charts` doesn't exist yet | Same fix |
| `curl http://localhost:8090/...` returns nothing | Another process is bound to 8090 (or controller metrics on 8080 if you used that port) | `lsof -nP -iTCP:8090 -sTCP:LISTEN` to identify, then pick a free port |
| Push returns `201` but `GET .../charts/X.tgz` is `404` | Filename mismatch between what was pushed and what you requested | The download URL must use `<chart-name>-<version>.tgz`, matching `Chart.yaml` fields exactly |

## Cleanup

```bash
docker rm -f chartmuseum
# Charts are gone because we didn't mount a volume. Nothing else to clean.
```

## Using this with the KubeVela controller

The negative-auth scenario in `kubevela/localtest/helmtest.yaml` points `chart.source` at `http://localhost:8090/charts/dockerpoc-app-5.0.1.tgz` with no `chart.auth.secretRef`. Applying it makes the controller's `fetchURLChart` do an anonymous `GET` against ChartMuseum, ChartMuseum returns `401`, and the Application workflow fails with the wrapped error visible in `kubectl describe application -n arc-system artifactory-helm-test`.

Once that error is observed, the positive scenario adds back a `kubernetes.io/basic-auth` Secret named `chartmuseum-creds` and a `chart.auth.secretRef` block referencing it, and the workflow turns green.

---

Last verified: 2026-05-21 — ChartMuseum image `ghcr.io/helm/chartmuseum:v0.16.2`, Rancher Desktop on macOS.
