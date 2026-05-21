# Build & Deploy Handbook

Operator-facing reference for the local build, container image, Kubernetes manifest, and Helm chart workflows. The [README](../README.md) has a quick reference; this doc explains the why, the sequence, and the failure modes.

## Contents

- [When to use what](#when-to-use-what)
- [Local Spring Boot run](#local-spring-boot-run)
- [Deploy.sh pipeline](#deploysh-pipeline)
- [Image tag synchronization](#image-tag-synchronization)
- [Make targets reference](#make-targets-reference)
- [Helm workflow](#helm-workflow)
- [Logging and Spring profiles](#logging-and-spring-profiles)
- [Troubleshooting](#troubleshooting)

## When to use what

| You want to… | Run | Notes |
|---|---|---|
| Hack on Java code, hit endpoints | IDE run with `-Dspring.profiles.active=dev` | Uses in-memory H2; logs to CONSOLE only |
| Verify the prod Docker image locally | `make build && docker run -p 8005:8005 vishal210893/dockerpoc-1:<tag>` | Image bakes `-Dspring.profiles.active=prod`; needs `-Ddb.password=…` reachable Postgres |
| Build a new image + push + sync manifest/chart, but don't deploy | `make build` | Steps 1–3b of `Deploy.sh` |
| Build and deploy onto an existing cluster | `./Deploy.sh --build` | Adds steps 4–6 (kubectl apply + port-forward) |
| Wipe and rebuild the local k3d cluster, then full deploy | `make deploy` | Calls `k3d cluster delete vela; k3d cluster create vela; ./Deploy.sh --build` |
| Apply existing manifests without rebuilding | `make run` (`./Deploy.sh` with no flags) | Use after a teammate pushes a new image |
| Tear down the app | `make delete` *or* `make helm-delete` | Depending on whether you deployed via kubectl or helm |
| Cut a chart tarball for distribution | `make helm-package` | Output in `dist/` (gitignored) |

## Local Spring Boot run

The app is a Spring Boot 3.4 service that listens on port **8005** under context path **`/dockerpoc`**. Two profiles drive everything:

| Profile | Datasource | Logging | Schema/data |
|---|---|---|---|
| `dev` | H2 in-memory (`jdbc:h2:mem:employeedb`) | CONSOLE coloured pattern | `db/schema.sql` + `db/data.sql` run on each start |
| `prod` | Aiven PostgreSQL (URL hard-coded in `application-prod.yaml`) | CONSOLE + rolling FILE | Same scripts run with PostgreSQL syntax |

The `dev` profile is the default when running from the IDE. The `prod` profile needs `-Ddb.password=…` to authenticate to Aiven. The `Dockerfile` bakes `-Dspring.profiles.active=prod` into `JAVA_OPTS` so any image runs in prod mode by default.

API surface (see `EmployeeController.java`, `SystemInfoController.java`):

```
GET    /dockerpoc/api/v1/version            JSON: microservice metadata + OS/JVM info
GET    /dockerpoc/api/v1/service            text: env name + hostname
POST   /dockerpoc/api/v1/numbers            JSON body {} → 900 descending ints
GET    /dockerpoc/api/v1/readfile           reads /opt/file/fileTest.txt from classpath (known buggy — file not packaged)

GET    /dockerpoc/api/employees             list
GET    /dockerpoc/api/employees/{id}        get one (404 if missing)
POST   /dockerpoc/api/employees             create (201)
PUT    /dockerpoc/api/employees/{id}        update (404 if missing)
DELETE /dockerpoc/api/employees/{id}        delete (204, or 404 if missing)
```

## Deploy.sh pipeline

`Deploy.sh` is the orchestrator for build + push + manifest sync + deploy. Mode is selected by a single flag:

```
./Deploy.sh                 # Steps 4-6 only
./Deploy.sh --build         # Steps 1-3b then 4-6
./Deploy.sh --build-only    # Steps 1-3b only
```

Internally the script sets `PERFORM_BUILD` and `PERFORM_DEPLOY` based on the flag, then guards the relevant blocks. Steps:

| Step | What happens |
|---|---|
| **1. Maven build** | `mvn -q install` (full test suite + JAR into `target/`) |
| **2. Docker build & push** | `TIMESTAMP=$(date +%Y%m%d-%H%M%S)`; builds `vishal210893/dockerpoc-1:${TIMESTAMP}` via `infra/docker/Dockerfile`; pushes to Docker Hub |
| **3. Patch K8s manifest** | `sed_inplace` replaces `image:` line in `infra/kubernetes/App/Deployment.yaml` |
| **3b. Patch Helm values** | `sed_inplace` replaces the `tag:` line in `infra/helm/dockerpoc-app/values.yaml` (gated on exactly one `tag:` line — refuses if shape is unexpected) |
| **4. Apply Ingress** | If no `ingress-nginx-controller` service is present, applies `infra/kubernetes/Ingress/*.yaml` and sleeps 90 s |
| **5. Apply Application** | `kubectl apply -f infra/kubernetes/App/Deployment.yaml`, sleeps 30 s |
| **6. Port-forward** | `kubectl port-forward svc/ingress-nginx-controller 8005:80 -n ingress-nginx` — blocks until Ctrl-C |

### sed_inplace helper

`sed -i'' -E '...'` is **not portable**. macOS BSD `sed` parses `-i''` as `-i` with backup extension `''`, then consumes the next argument (`-E` or `-e`) as the *real* extension — leaving the regex flag unset. The script therefore defines its own helper that writes to a sibling temp file and renames:

```bash
sed_inplace() {
  local file
  for file in "$@"; do :; done   # last arg is the target file
  local tmp="${file}.tmp.$$"
  sed "$@" > "$tmp" && mv "$tmp" "$file"
}
```

This works identically on macOS, Linux, the devcontainer, and CI runners. No backup files left behind.

## Image tag synchronization

A single timestamp drives three places:

```
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
IMAGE="vishal210893/dockerpoc-1:${TIMESTAMP}"

# 1. Container registry
docker build -f infra/docker/Dockerfile -t "$IMAGE" .
docker push "$IMAGE"

# 2. K8s manifest (raw kubectl-apply path)
infra/kubernetes/App/Deployment.yaml      # image: vishal210893/dockerpoc-1:<TIMESTAMP>

# 3. Helm values (helm-install path)
infra/helm/dockerpoc-app/values.yaml      # tag: "<TIMESTAMP>"
```

After `make build`, both `kubectl apply -f infra/kubernetes/App/Deployment.yaml` **and** `make helm-install` will land the freshly-pushed image. No more drift between the two paths.

CI does the same alignment in `.github/workflows/build-and-push.yml` — the `update-deployment` job uses `sed` on the K8s manifest, then commits and pushes back to the branch. (The Helm `values.yaml` is **not** rewritten by CI today; it picks up the new tag only when a developer runs `make build` locally and commits.)

## Make targets reference

| Target | Underlying command | When to use |
|---|---|---|
| `make build` | `./Deploy.sh --build-only` | Develop locally, push a new image, sync chart + manifest |
| `make deploy` | `k3d cluster delete vela; k3d cluster create vela; ./Deploy.sh --build` | Fresh local cluster + full deploy |
| `make run` | `./Deploy.sh` | Apply existing manifests, no rebuild |
| `make delete` | `kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found` | Remove app via kubectl path |
| `make helm-install` | `helm install dockerpoc ./infra/helm/dockerpoc-app` | Install Helm release |
| `make helm-delete` | `helm delete dockerpoc \|\| true` | Remove Helm release (idempotent) |
| `make helm-redeploy` | `helm-delete && helm-install && port-forward` | Clean reinstall + access |
| `make helm-package` | `helm lint && helm package -d dist` | Cut a `.tgz` for sharing or upload |
| `make port-forward` | `kubectl port-forward svc/ingress-nginx-controller 8005:80 -n ingress-nginx` | Quick local access via ingress |

The Makefile uses tab indentation (Make requirement) and all targets are declared `.PHONY` since none produce a file matching the target name.

## Helm workflow

The chart lives at `infra/helm/dockerpoc-app/`. The directory name matches `Chart.yaml`'s `name: dockerpoc-app` so Helm tooling (chart-releaser, OCI registries) handles it without surprises.

Three flows exist:

### Local install

```bash
make helm-install                  # release name: dockerpoc
helm status dockerpoc
helm get values dockerpoc
```

For a one-off override (e.g. point at a locally-built image), pass `--set`:

```bash
helm upgrade --install dockerpoc ./infra/helm/dockerpoc-app \
  --set deployment.container.image=dockerpoc \
  --set deployment.container.tag=reorg-verify \
  --set deployment.container.imagePullPolicy=Never \
  --set ingress.enabled=false
```

### Packaging

`make helm-package` runs `helm lint` then `helm package -d dist`. The resulting `dist/dockerpoc-app-<chart-version>.tgz` can be:

- pushed to a **classic HTTPS Helm repository** (e.g. ChartMuseum — see [push-chart-to-local-chartmuseum.md](push-chart-to-local-chartmuseum.md) for a local setup) via `POST /api/charts`,
- pushed to an **OCI registry** (e.g. Docker Hub OCI, zot) via `helm push oci://…` (different protocol — `oras` push under the hood),
- or attached to a GitHub release / served via the `gh-pages` Helm repo that chart-releaser already maintains for this project.

### CI release (`helm-release.yml`)

`helm/chart-releaser-action` is configured with `charts_dir: infra/helm`. On every push to `learning/github-action` it walks subdirectories of `infra/helm/`, packages any new chart version it finds, and publishes to the `gh-pages` branch. The chart version is the `version:` field in `Chart.yaml` — bump it deliberately when shipping changes.

## Logging and Spring profiles

Configuration: `src/main/resources/logback-spring.xml`. The `-spring.xml` suffix is required for `<springProfile>` blocks to activate; with plain `logback.xml`, Spring Boot can't intercept the file and the conditional blocks become no-ops.

Behaviour:

| Profile | root + `com.learning.docker` |
|---|---|
| `dev` | CONSOLE (coloured pattern) only |
| `prod` | CONSOLE + rolling FILE → `logs/dockerpoc.log` |
| other | No custom root logger; Spring Boot default config applies |

The rolling file policy in `prod`:

- Path: `logs/dockerpoc.log`
- Rollover: daily *or* on reaching 100 MB
- Compressed archive name: `logs/dockerpoc-yyyy-MM-dd.<idx>.log.gz`
- Retention: 30 days
- Total archive size cap: 1 GB

### Why two `<root>` blocks?

Spring Boot's `SpringProfileIfNestedWithinSecondPhaseElementSanityChecker` forbids `<springProfile>` **inside** a `<root>`, `<logger>`, or `<appender>` element. The supported pattern is:

```xml
<springProfile name="dev">
  <root>...</root>
  <logger ...>...</logger>
</springProfile>
<springProfile name="prod">
  <root>...</root>
  <logger ...>...</logger>
</springProfile>
```

The duplication is annoying but necessary. Symptom of getting this wrong: Spring Boot emits a `WARN` about the nesting at startup, then silently drops the entire `<root>` declaration. The app starts but no custom logging appears — you see the banner from `System.out` and nothing else.

## Troubleshooting

### "sed: \1 not defined in the RE" on macOS

You hit BSD sed's `-i''` parser bug. The `-i'' -E` combination is parsed as `-i` with extension `-E`, so the regex flag is gone and `(...)` is literal. **Fix:** use the `sed_inplace` helper in `Deploy.sh`. If you wrote a script yourself, use the same temp-file pattern.

**Side effect on past runs:** the original `Deploy.sh` had this bug. Each `make build` created a `Deployment.yaml-e` backup file in `infra/kubernetes/App/`. Clean these up:

```bash
find infra/kubernetes/ -name '*-e' -delete
```

### `make build` runs Maven but Spring Boot integration tests can't bind ports

The Maven build phase runs the full test suite, including `@SpringBootTest` integration tests. They start an embedded Tomcat on a random port and shouldn't conflict — but if the build is hanging, check whether your IDE is also running the app and holding port 8005.

### Helm install pulls the wrong image

Check the chart's current values:

```bash
grep -nE "image:|tag:" infra/helm/dockerpoc-app/values.yaml
```

After `make build` it should show the latest timestamp. If it doesn't, `make build` either skipped Step 3b (unexpected number of `tag:` lines in `values.yaml` — script logs `⚠ Skipped`) or you ran without `--build` / `--build-only`. Re-run `make build`.

### Logback says "Could NOT find resource [logback.xml]"

That's fine — we use `logback-spring.xml` instead. The Could-Not-Find message is logback's initial probe before Spring Boot's `SpringBootJoranConfigurator` takes over. If you also see `Trying to configure with ch.qos.logback.classic.BasicConfigurator` **and** never see your colored pattern, then `logback-spring.xml` wasn't loaded — check that `src/main/resources/logback-spring.xml` exists and that `target/classes/logback-spring.xml` was refreshed by `mvn process-resources` or an IDE rebuild.

### Pod stays in `ErrImagePull` / `ImagePullBackOff`

The published image tag may have been GC'd from Docker Hub (older "learning" tags get pruned). Either:

1. Run `make build` locally to push a fresh tag and patch `values.yaml`, then `helm upgrade --install`.
2. For local k3d clusters, import the locally-built image directly:
   ```bash
   k3d image import dockerpoc:<tag> -c <cluster-name>
   ```
   Then install with `--set deployment.container.imagePullPolicy=Never`.

### Logs are missing in dev mode (in IDE)

After updating `logback-spring.xml`, IntelliJ may still be running the previous compiled resource from `target/classes/`. Refresh it:

- IntelliJ: **Build → Build Project** (Cmd+F9 / Ctrl+F9), then re-run.
- Terminal: `mvn -q process-resources` from the repo root.
