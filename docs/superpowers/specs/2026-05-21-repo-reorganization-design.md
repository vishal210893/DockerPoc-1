# Repository Reorganization — Design

- **Date:** 2026-05-21
- **Target branch:** `learning/github-action` (de-facto trunk; `origin/HEAD` points here)
- **Branch strategy:** commit directly on `learning/github-action` (single atomic commit)
- **Author:** Vishal (`viskumar`), drafted with Claude

## 1. Goal and scope

`learning/github-action` has accumulated a flat top-level layout, tracked IDE state with gaps, and a vendored `node_modules/` under `.github/actions/`. The reorganization regroups deployment artefacts under a single `infra/` tree, lifts loose markdown into `docs/`, untracks build/IDE noise, and brings the gitignore back in line with what is actually shipped.

**In scope**
- Move deployment-adjacent directories and Dockerfiles under `infra/`.
- Move loose markdown documentation under `docs/`.
- Rename the Helm chart directory to match the chart's `name:` field.
- Rewrite `.gitignore` to catch `.DS_Store`, `node_modules/`, loose `*.iml`; keep `.idea/` tracked.
- Untrack tracked-by-accident files: vendored `node_modules`, loose `.iml`, stray `test` file.
- Rewrite every call-site (CI workflows, Makefile, `Deploy.sh`, `Dockerfile`, docs) that references a moved path.
- Verify locally before committing.

**Out of scope**
- Renaming the Helm chart itself (`name: dockerpoc-app` stays).
- Restructuring Java source layout under `src/`.
- Re-organising `.github/actions/javascript/` internals beyond untracking `node_modules`.
- Re-organising `infra/flux/Doc/*.md` content (those are aspirational layout docs; leave as-is).
- Touching the open feature-branch work stashed at `stash@{0}`.

## 2. Target layout

```
/                                  Spring Boot module root
├── README.md                      <- Readme.md (renamed)
├── pom.xml                        unchanged
├── Makefile                       internal paths updated
├── Deploy.sh                      stays at root; internal paths updated
├── .gitignore                     rewritten
├── src/                           unchanged
├── target/                        gitignored
├── logs/                          gitignored
├── infra/
│   ├── docker/
│   │   ├── Dockerfile                  <- Dockerfile
│   │   ├── Dockerfile.maven-build      <- dockerfile_with_maven_as_build
│   │   ├── docker-compose.yml          <- docker-compose.yml
│   │   └── nginx/                      <- nginx/
│   ├── kubernetes/                <- K8s_Yaml/
│   ├── helm/
│   │   └── dockerpoc-app/         <- charts/Helm/  (renamed to match Chart.yaml name)
│   ├── terraform/                 <- Terraform/
│   └── flux/                      <- clusters/flux/
├── docs/
│   ├── helm-terraform.md          <- HELM_TERRAFORM.md
│   ├── remote-debugging.md        <- RemoteDebbuging.md (typo fixed)
│   ├── artifacthub-repo.yml       <- artifacthub-repo.yml
│   └── superpowers/specs/         design specs (this file)
├── .idea/                         kept tracked
├── .github/
│   ├── workflows/                 path strings updated
│   └── actions/javascript/        node_modules/ untracked; dist/ kept
└── .vscode/                       gitignored
```

## 3. File moves, renames, untracks

### 3.1 Directory moves (`git mv` so history follows)

| Source | Destination |
|---|---|
| `K8s_Yaml/` | `infra/kubernetes/` |
| `charts/Helm/` | `infra/helm/dockerpoc-app/` |
| `Terraform/` | `infra/terraform/` |
| `clusters/flux/` | `infra/flux/` |
| `nginx/` | `infra/docker/nginx/` |

After moves, remove now-empty `charts/` and `clusters/` parents.

### 3.2 File moves and renames

| Source | Destination |
|---|---|
| `Dockerfile` | `infra/docker/Dockerfile` |
| `dockerfile_with_maven_as_build` | `infra/docker/Dockerfile.maven-build` |
| `docker-compose.yml` | `infra/docker/docker-compose.yml` |
| `HELM_TERRAFORM.md` | `docs/helm-terraform.md` |
| `RemoteDebbuging.md` | `docs/remote-debugging.md` *(typo fixed in filename)* |
| `artifacthub-repo.yml` | `docs/artifacthub-repo.yml` |
| `Readme.md` | `README.md` *(canonical casing for GitHub)* |

### 3.3 Untrack (kept on disk; ignored going forward)

- `.github/actions/javascript/node_modules/` — recursive `git rm --cached`. Safe because the action's `runs.main` is `dist/index.js` (an `ncc`-bundled file), so `node_modules` is only needed at developer build time.
- `docker-poc-1.iml` — `.idea/modules.xml` already references the module; loose `.iml` is redundant.
- `test` — 3 KB stray file at root, already deleted in the feature branch's working tree. Remove with `git rm -- test` to avoid argument ambiguity.

## 4. `.gitignore` (full rewrite)

```
# Build output
target/
build/
.gradle

# Logs
logs/

# OS / IDE-local
.DS_Store
Thumbs.db
.vscode/
.classpath
.project
.settings
.springBeans
.sts4-cache
.apt_generated
.factorypath

# Loose IntelliJ module files (.idea/ itself stays tracked)
*.iml
*.iws
*.ipr

# Node
node_modules/

# Misc
HELP.md
env/
/bin/
/nbproject/private/
/nbbuild/
/nbdist/
/.nb-gradle/
/dist/
```

Net effect: `.idea/` stays tracked (per user requirement); `.DS_Store`, `node_modules/`, `*.iml`, `target/`, `logs/` are ignored.

## 5. Call-site rewrites

### 5.1 `.github/workflows/build-and-push.yml`

| Location | Before | After |
|---|---|---|
| `update-deployment` job (×2) | `DEPLOYMENT_FILE="K8s_Yaml/App/Deployment.yaml"` | `DEPLOYMENT_FILE="infra/kubernetes/App/Deployment.yaml"` |
| same job, git status check | `git status --porcelain K8s_Yaml/App/Deployment.yaml` | `git status --porcelain infra/kubernetes/App/Deployment.yaml` |
| same job, git add | `git add K8s_Yaml/App/Deployment.yaml` | `git add infra/kubernetes/App/Deployment.yaml` |
| `docker-build-push` job | `file: ./Dockerfile` | `file: ./infra/docker/Dockerfile` |

Build context stays `.` (repo root); only the Dockerfile location changes.

The first line of this workflow is currently mashed onto one row (`name: Build and Deployon:  push:` …) — clear corruption from a prior edit. The same pass re-indents the file.

### 5.2 `.github/workflows/helm-release.yml`

| Before | After |
|---|---|
| `charts_dir: charts` | `charts_dir: infra/helm` |

`chart-releaser-action` scans subdirectories of `charts_dir`, so it will find `infra/helm/dockerpoc-app/Chart.yaml`.

### 5.3 `Deploy.sh`

```diff
- K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/K8s_Yaml/App/Deployment.yaml"
- K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/K8s_Yaml/Ingress"
+ K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/infra/kubernetes/App/Deployment.yaml"
+ K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/infra/kubernetes/Ingress"

- [[ -f "$SCRIPT_DIR/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }
+ [[ -f "$SCRIPT_DIR/infra/docker/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }

- docker build -t "$IMAGE" "$SCRIPT_DIR"
+ docker build -f "${SCRIPT_DIR}/infra/docker/Dockerfile" -t "$IMAGE" "$SCRIPT_DIR"
```

### 5.4 `Makefile`

```diff
- kubectl delete -f K8s_Yaml/App/Deployment.yaml --ignore-not-found
+ kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found

- helm install dockerpoc ./Helm                    (×2; pre-existing bug — ./Helm never existed)
+ helm install dockerpoc ./infra/helm/dockerpoc-app
```

### 5.5 `infra/docker/Dockerfile`

```diff
- COPY nginx/ /tmp/
+ COPY infra/docker/nginx/ /tmp/
```

Build context remains the repo root, so `COPY` paths must be rooted there.

`infra/docker/Dockerfile.maven-build` and `infra/docker/docker-compose.yml` will be scanned for similar path references on the same pass and patched where needed.

### 5.6 `infra/flux/helm-flux.yaml`

Only a commented line today, but updated for accuracy:

```diff
-#      chart: ./charts/Helm
+#      chart: ./infra/helm/dockerpoc-app
```

### 5.7 Documentation (cosmetic; no functional impact)

| File | Edits |
|---|---|
| `README.md` *(was Readme.md)* | `K8s_Yaml` → `infra/kubernetes` (×3); `charts/Helm` → `infra/helm/dockerpoc-app` (×3) |
| `docs/helm-terraform.md` | `charts/` → `infra/helm/` in the directory tree |
| `docs/remote-debugging.md` | scan for path mentions and update; replace any in-body occurrences of "Debbuging" → "Debugging" |
| `infra/flux/Doc/*.md` | left as-is (aspirational layout docs) |

## 6. Execution order

Single atomic commit. Steps:

1. Write new `.gitignore`.
2. Untrack noise: `git rm --cached -r .github/actions/javascript/node_modules`; `git rm --cached docker-poc-1.iml`; `git rm test`.
3. Directory moves via `git mv` (see §3.1); remove empty `charts/` and `clusters/` parents.
4. File moves and renames via `git mv` (see §3.2).
5. Call-site rewrites (see §5).
6. Local verification (§7).
7. Single commit: `refactor(repo): regroup deploy artefacts under infra/, normalize layout`.

## 7. Verification before commit

| Check | Command | Success criterion |
|---|---|---|
| Maven build | `mvn -B -q install` | exit 0; `target/dockerpoc-1.jar` exists |
| Dockerfile build | `docker build -f infra/docker/Dockerfile -t dockerpoc:reorg-check .` | builds successfully |
| Helm template | `helm template dockerpoc ./infra/helm/dockerpoc-app` | renders without errors |
| Helm lint | `helm lint ./infra/helm/dockerpoc-app` | no errors (warnings OK) |
| Deploy.sh syntax | `bash -n Deploy.sh` | exit 0 |
| Deploy.sh path constants resolve | `bash -c 'set -e; SCRIPT_DIR=$(pwd); source <(sed -n "1,30p" Deploy.sh); [[ -f "$K8S_APP_MANIFEST_FILE" ]] && [[ -d "$K8S_INGRESS_MANIFEST_PATH" ]]'` | exit 0 |
| Workflow YAML parses | `python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/<file>` | no parse error |
| Path grep sweep | `grep -RnE 'K8s_Yaml\|charts/Helm\|^nginx/\|Terraform/\|clusters/flux' --exclude-dir={.git,target,node_modules,.idea}` | only legitimate hits (e.g. `infra/flux/Doc/*.md` aspirational docs, or already-updated diff blocks in this design file) |

## 8. Risks and mitigations

- **`helm-release.yml` triggers on push to `learning/github-action`.** Misconfigured `charts_dir` causes a workflow failure. Mitigation: dry-run with `helm lint` and `helm template` against the new path before push; fix-forward if the published action behaves differently.
- **`build-and-push.yml` triggers on push to `main` only** (line 1). It will not run from this reorg landing on `learning/github-action`, so the K8s path edits there are unverified until the next merge to `main`. Mitigation: spot-check the YAML by hand and run a `yaml.safe_load` parse.
- **`.idea/` content drift.** `.idea/workspace.xml` and other per-developer files in `.idea/` change every IDE session and will create commit noise even though the dir is tracked. Out of scope for this commit, but flagged for future cleanup (`.gitignore` entry for `.idea/workspace.xml` etc. would help).
- **Maven build inside the devcontainer** has not yet been run on this branch; the verification step will surface any unrelated breakage.

## 9. Branch and merge strategy

Commit directly on `learning/github-action` (Option 1, auto-mode default).

Rationale: the user is the sole maintainer; the commit is atomic and revertible; `helm-release.yml` will exercise the chart path on first push, providing immediate feedback.

## 10. Out-of-band state

- `stash@{0}` holds the prior `claude/video-transcripts-documentation-Jy0Dq` working-tree drift. Not addressed by this reorg; recoverable via `git stash list`.
- `udemy_docs/` and the Udemy transcript script live on the feature branch only and are not touched here.
