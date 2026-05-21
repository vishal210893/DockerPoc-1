# Repo Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Regroup `DockerPoc-1`'s deployment artefacts under a single `infra/` tree, lift loose markdown into `docs/`, rename the Helm chart dir to match `Chart.yaml`, untrack IDE/build noise, and update every call-site that references a moved path. End state: a single atomic commit on `learning/github-action`.

**Architecture:** Pure refactor. Five directory moves + four file moves + two renames + ten or so call-site edits across CI workflows, scripts, the Dockerfile, the Makefile, and docs. Single commit at the end (per design spec §6) because no intermediate state is functional — CI paths and script paths must all flip in lockstep.

**Tech Stack:** Git (`git mv` to preserve history), Bash, Maven 3.9 / Java 17, Docker, Helm 3, GitHub Actions, FluxCD. All work happens inside the existing devcontainer at `/workspaces/DockerPoc-1`.

**Design spec:** `docs/superpowers/specs/2026-05-21-repo-reorganization-design.md` — read it before starting; this plan is the execution arm of that spec.

**Key constraints (from the spec):**
- `.idea/` stays tracked.
- `Deploy.sh` stays at root.
- Helm chart directory renamed to match `Chart.yaml` `name:` field → `dockerpoc-app/`.
- One atomic commit, message: `refactor(repo): regroup deploy artefacts under infra/, normalize layout`.
- Branch: `learning/github-action` (already checked out).

**Conventions used in this plan:**
- Every shell command is intended to be run from `/workspaces/DockerPoc-1` (the repo root) unless otherwise stated.
- After each task, the file listing changes but **no commit is made until Task E7** — staged changes accumulate.
- Each task ends with a quick "verify" step. If verification fails, stop and ask for guidance.

---

## Phase A — Setup

### Task A1: Pre-flight verification

**Files:** none (read-only).

- [ ] **Step 1: Confirm branch and clean working tree**

Run:
```bash
git -C /workspaces/DockerPoc-1 rev-parse --abbrev-ref HEAD
git -C /workspaces/DockerPoc-1 status --short
```

Expected:
```
learning/github-action
```
Followed by an empty `git status --short` (no output). If there are any modified or untracked files (other than what's expected from the running session), stop and ask.

- [ ] **Step 2: Confirm parent commit**

Run:
```bash
git -C /workspaces/DockerPoc-1 log --oneline -1
```

Expected: `cf9a25e chore(deploy): Update Kubernetes deployment image …`

- [ ] **Step 3: Capture pre-state file count for sanity later**

Run:
```bash
git -C /workspaces/DockerPoc-1 ls-files | wc -l > /tmp/reorg-pre-count
cat /tmp/reorg-pre-count
```

Expected: `539` (or whatever it is — recording for comparison). The count will drop by roughly the `node_modules/` size (~389 files) and rise slightly from the markdown/spec docs added by this plan.

### Task A2: Create the new skeleton directories

**Files:** none yet — only directories.

- [ ] **Step 1: Create `infra/` subtree and `docs/`**

Run:
```bash
mkdir -p /workspaces/DockerPoc-1/infra/docker \
         /workspaces/DockerPoc-1/infra/helm \
         /workspaces/DockerPoc-1/docs
```

- [ ] **Step 2: Verify**

Run:
```bash
ls -d /workspaces/DockerPoc-1/infra/docker \
      /workspaces/DockerPoc-1/infra/helm \
      /workspaces/DockerPoc-1/docs
```

Expected: all three paths print, no errors.

Note: `infra/kubernetes/`, `infra/terraform/`, `infra/flux/`, `infra/helm/dockerpoc-app/`, `infra/docker/nginx/` will be created implicitly by `git mv` in Phase C — no need to `mkdir` them ahead of time.

### Task A3: Write the new `.gitignore`

**Files:**
- Modify: `/workspaces/DockerPoc-1/.gitignore`

- [ ] **Step 1: Overwrite `.gitignore` with the new content**

Replace the entire file with:
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

- [ ] **Step 2: Stage and verify**

Run:
```bash
git -C /workspaces/DockerPoc-1 add .gitignore
git -C /workspaces/DockerPoc-1 diff --cached -- .gitignore | head -60
```

Expected: a `diff --git a/.gitignore b/.gitignore` block showing the new content replacing the old. `.idea/` must NOT appear as an ignore entry.

- [ ] **Step 3: Verify `.idea/` still tracked, `.DS_Store` ignored**

Run:
```bash
git -C /workspaces/DockerPoc-1 check-ignore -v .DS_Store .idea/compiler.xml 2>&1 || true
```

Expected:
```
.gitignore:11:.DS_Store	.DS_Store
```
(only `.DS_Store` reports an ignore match; `.idea/compiler.xml` does not — it's tracked, so `check-ignore` exits non-zero for that path, which is fine).

---

## Phase B — Untrack noise

### Task B1: Untrack vendored `node_modules`, loose `.iml`, and stray `test`

**Files:**
- Untrack: `.github/actions/javascript/node_modules/` (recursive)
- Untrack: `docker-poc-1.iml`
- Untrack/remove: `test` (also delete from working tree — design spec §3.3)

- [ ] **Step 1: Untrack `node_modules` (keep on disk)**

Run:
```bash
git -C /workspaces/DockerPoc-1 rm --cached -r .github/actions/javascript/node_modules
```

Expected: many lines of `rm '.github/actions/javascript/node_modules/...'`. The files remain on disk because `--cached` only removes from the index.

- [ ] **Step 2: Untrack loose `.iml`**

Run:
```bash
git -C /workspaces/DockerPoc-1 rm --cached docker-poc-1.iml
```

Expected: `rm 'docker-poc-1.iml'`.

- [ ] **Step 3: Remove stray `test` file**

Run:
```bash
git -C /workspaces/DockerPoc-1 rm -- test
```

Expected: `rm 'test'`. The `--` separator avoids ambiguity with any future `test/` directory.

- [ ] **Step 4: Verify**

Run:
```bash
git -C /workspaces/DockerPoc-1 status --short | head -10
git -C /workspaces/DockerPoc-1 ls-files .github/actions/javascript/node_modules/ | head -3
test -f /workspaces/DockerPoc-1/.github/actions/javascript/node_modules/package.json && echo "node_modules still on disk: OK"
test -f /workspaces/DockerPoc-1/docker-poc-1.iml && echo "docker-poc-1.iml still on disk: OK"
test ! -f /workspaces/DockerPoc-1/test && echo "test file removed: OK"
```

Expected:
- `git status --short` shows `D  .github/actions/javascript/node_modules/...` lines (capital D = deleted from index) and `D  docker-poc-1.iml`, `D  test`.
- `ls-files` shows nothing (no longer tracked).
- All three "OK" lines print.

---

## Phase C — Directory and file moves

All moves use `git mv` so history follows the file. After each move, run a quick verify with `git status --short` to confirm the rename was detected.

### Task C1: Move `K8s_Yaml/` → `infra/kubernetes/`

- [ ] **Step 1: Move**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv K8s_Yaml infra/kubernetes
```

- [ ] **Step 2: Verify**

Run:
```bash
git -C /workspaces/DockerPoc-1 status --short | grep -E '^R.*infra/kubernetes' | head -3
test -d /workspaces/DockerPoc-1/infra/kubernetes/App && echo "App subdir present: OK"
test ! -d /workspaces/DockerPoc-1/K8s_Yaml && echo "Old dir gone: OK"
```

Expected: `R` (rename) lines, both "OK" lines.

### Task C2: Move `charts/Helm/` → `infra/helm/dockerpoc-app/`

- [ ] **Step 1: Move**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv charts/Helm infra/helm/dockerpoc-app
```

- [ ] **Step 2: Remove now-empty `charts/` parent**

Run:
```bash
rmdir /workspaces/DockerPoc-1/charts
```

Expected: silent success. If it complains "Directory not empty", list the contents (`ls /workspaces/DockerPoc-1/charts`) and decide whether the remaining content should also move under `infra/helm/`. As of the current state on `learning/github-action`, `charts/` only contains `Helm/`, so `rmdir` should succeed.

- [ ] **Step 3: Verify**

Run:
```bash
test -f /workspaces/DockerPoc-1/infra/helm/dockerpoc-app/Chart.yaml && echo "Chart.yaml moved: OK"
test ! -d /workspaces/DockerPoc-1/charts && echo "charts/ gone: OK"
```

### Task C3: Move `Terraform/` → `infra/terraform/`

- [ ] **Step 1: Move**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv Terraform infra/terraform
```

- [ ] **Step 2: Verify**

Run:
```bash
test -d /workspaces/DockerPoc-1/infra/terraform && echo "moved: OK"
test ! -d /workspaces/DockerPoc-1/Terraform && echo "old gone: OK"
```

### Task C4: Move `clusters/flux/` → `infra/flux/`

- [ ] **Step 1: Move**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv clusters/flux infra/flux
```

- [ ] **Step 2: Remove `clusters/` if empty**

Run:
```bash
rmdir /workspaces/DockerPoc-1/clusters 2>/dev/null && echo "clusters/ removed" || echo "clusters/ not empty (inspect)"
ls /workspaces/DockerPoc-1/clusters 2>/dev/null || true
```

If `clusters/` has other content, stop and decide before continuing. At the current state, `clusters/` only contains `flux/`, so it should be removed silently.

- [ ] **Step 3: Verify**

Run:
```bash
test -f /workspaces/DockerPoc-1/infra/flux/helm-flux.yaml && echo "helm-flux.yaml moved: OK"
```

### Task C5: Move `nginx/` → `infra/docker/nginx/`

- [ ] **Step 1: Move**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv nginx infra/docker/nginx
```

- [ ] **Step 2: Verify**

Run:
```bash
test -f /workspaces/DockerPoc-1/infra/docker/nginx/Dockerfile-nginx && echo "nginx files moved: OK"
test ! -d /workspaces/DockerPoc-1/nginx && echo "old gone: OK"
```

### Task C6: Move Dockerfiles and `docker-compose.yml` → `infra/docker/`

- [ ] **Step 1: Move all three**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv Dockerfile infra/docker/Dockerfile
git -C /workspaces/DockerPoc-1 mv dockerfile_with_maven_as_build infra/docker/Dockerfile.maven-build
git -C /workspaces/DockerPoc-1 mv docker-compose.yml infra/docker/docker-compose.yml
```

- [ ] **Step 2: Verify**

Run:
```bash
ls -la /workspaces/DockerPoc-1/infra/docker/
```

Expected: `Dockerfile`, `Dockerfile.maven-build`, `docker-compose.yml`, and `nginx/` directory all present.

### Task C7: Move loose markdown and YAML into `docs/`

- [ ] **Step 1: Move three files**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv HELM_TERRAFORM.md docs/helm-terraform.md
git -C /workspaces/DockerPoc-1 mv RemoteDebbuging.md docs/remote-debugging.md
git -C /workspaces/DockerPoc-1 mv artifacthub-repo.yml docs/artifacthub-repo.yml
```

- [ ] **Step 2: Verify**

Run:
```bash
ls /workspaces/DockerPoc-1/docs/
```

Expected: `artifacthub-repo.yml  helm-terraform.md  remote-debugging.md  superpowers/`

---

## Phase D — Call-site rewrites

### Task D1: Patch `infra/docker/Dockerfile` — fix `COPY nginx/`

**Files:**
- Modify: `infra/docker/Dockerfile` line 29

The build context is the repo root (set by `Deploy.sh` and CI). After the `nginx/` move, the `COPY` source path must be `infra/docker/nginx/`, not `nginx/`.

- [ ] **Step 1: Edit line 29**

Replace exactly:
```
COPY nginx/ /tmp/
```
with:
```
COPY infra/docker/nginx/ /tmp/
```

- [ ] **Step 2: Verify**

Run:
```bash
grep -n "COPY infra/docker/nginx/" /workspaces/DockerPoc-1/infra/docker/Dockerfile
grep -nE "^COPY nginx/" /workspaces/DockerPoc-1/infra/docker/Dockerfile || echo "old COPY line gone: OK"
```

Expected: first grep shows `29:COPY infra/docker/nginx/ /tmp/`; second grep prints "old COPY line gone: OK".

### Task D2: Patch `infra/docker/docker-compose.yml` — fix build context

**Files:**
- Modify: `infra/docker/docker-compose.yml` lines 20-24 (the `dockerpoc-1` service `build:` block).

After moving `docker-compose.yml` from repo root into `infra/docker/`, `context: .` now resolves to `infra/docker/`, not the repo root. That breaks `COPY target/dockerpoc-1.jar` inside the Dockerfile. Fix by anchoring the context back at repo root.

The `nginx` service's `context: ./nginx` (line 13) is already correct after the move (it now resolves to `infra/docker/nginx/`, which is where the nginx files are). Leave that alone.

The `dockerpoc-2` service (lines 34-46) uses an absolute path on the user's host — unrelated to this reorg, leave alone.

- [ ] **Step 1: Replace lines 20-24**

Old:
```yaml
  dockerpoc-1:
    build:
      # context: specify the location of dockerfile (.(dot) = current working directory)
      context: .
      # To specify name of dockerfile
      dockerfile: dockerfile
```

New:
```yaml
  dockerpoc-1:
    build:
      # context: repo root (two levels up from this compose file)
      context: ../..
      # Dockerfile path relative to context
      dockerfile: infra/docker/Dockerfile
```

- [ ] **Step 2: Verify**

Run:
```bash
grep -nE "context: \.\./\.\.|dockerfile: infra/docker/Dockerfile" /workspaces/DockerPoc-1/infra/docker/docker-compose.yml
```

Expected: two matches for the new lines.

- [ ] **Step 3: YAML parse**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('/workspaces/DockerPoc-1/infra/docker/docker-compose.yml'))" && echo "YAML parses: OK"
```

Expected: `YAML parses: OK`.

### Task D3: Patch `Deploy.sh`

**Files:**
- Modify: `Deploy.sh` lines 22-23, 49, 65

- [ ] **Step 1: Update K8s manifest paths (lines 22-23)**

Old:
```bash
K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/K8s_Yaml/App/Deployment.yaml"
K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/K8s_Yaml/Ingress"
```

New:
```bash
K8S_APP_MANIFEST_FILE="${SCRIPT_DIR}/infra/kubernetes/App/Deployment.yaml"
K8S_INGRESS_MANIFEST_PATH="${SCRIPT_DIR}/infra/kubernetes/Ingress"
```

- [ ] **Step 2: Update Dockerfile sanity check (line 49)**

Old:
```bash
  [[ -f "$SCRIPT_DIR/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }
```

New:
```bash
  [[ -f "$SCRIPT_DIR/infra/docker/Dockerfile" ]] || { echo "ERROR: Dockerfile missing"; exit 1; }
```

- [ ] **Step 3: Update docker build command (line 65)**

Old:
```bash
  docker build -t "$IMAGE" "$SCRIPT_DIR"
```

New:
```bash
  docker build -f "${SCRIPT_DIR}/infra/docker/Dockerfile" -t "$IMAGE" "$SCRIPT_DIR"
```

- [ ] **Step 4: Verify**

Run:
```bash
grep -nE "K8s_Yaml" /workspaces/DockerPoc-1/Deploy.sh && echo "STILL HAS K8s_Yaml — FAIL" || echo "no K8s_Yaml left: OK"
grep -nE "infra/kubernetes|infra/docker/Dockerfile" /workspaces/DockerPoc-1/Deploy.sh
bash -n /workspaces/DockerPoc-1/Deploy.sh && echo "syntax OK"
```

Expected: "no K8s_Yaml left: OK", four matches for the new paths, and "syntax OK".

### Task D4: Patch `Makefile`

**Files:**
- Modify: `Makefile` lines 10, 16, 23

- [ ] **Step 1: Update line 10**

Old:
```
	kubectl delete -f K8s_Yaml/App/Deployment.yaml --ignore-not-found
```

New:
```
	kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found
```

- [ ] **Step 2: Update lines 16 and 23 (`./Helm` is a pre-existing bug — the path was never `./Helm`, the chart was at `charts/Helm/`)**

Old (both lines):
```
	helm install dockerpoc ./Helm
```

New (both lines):
```
	helm install dockerpoc ./infra/helm/dockerpoc-app
```

- [ ] **Step 3: Verify**

Run:
```bash
grep -nE "K8s_Yaml|\./Helm( |$)" /workspaces/DockerPoc-1/Makefile && echo "OLD PATHS STILL PRESENT — FAIL" || echo "old paths gone: OK"
grep -nE "infra/kubernetes|infra/helm/dockerpoc-app" /workspaces/DockerPoc-1/Makefile
```

Expected: "old paths gone: OK", three matches for the new paths.

### Task D5: Patch `.github/workflows/build-and-push.yml`

**Files:**
- Modify: `.github/workflows/build-and-push.yml`

Two things to fix:
1. The first line of the file is corrupted — `name: Build and Deployon:  push:    branches:      …` is all squashed onto one row. Re-indent the file with proper newlines.
2. Replace four `K8s_Yaml` references and the `file: ./Dockerfile` reference.

- [ ] **Step 1: Read the file fully**

Run:
```bash
wc -l /workspaces/DockerPoc-1/.github/workflows/build-and-push.yml
```

Note the line count. If the file is one big line (corruption), `wc -l` will report a small number relative to its content. From the design spec we already know the first line is malformed.

- [ ] **Step 2: Fix line 1 (the corruption) and all paths in one pass**

Replace the literal opening block:
```
name: Build and Deployon:  push:    branches:      # - learning/github-action      - main  workflow_dispatch:    inputs:      reason:        description: "Reason for running the workflow"        required: false        default: "Manual execution"# Global environment variables accessible in all jobsenv:  USER: vishal210893  REPO_NAME: dockerpoc-1jobs:  build-artifact:
```
with:
```
name: Build and Deploy

on:
  push:
    branches:
      # - learning/github-action
      - main
  workflow_dispatch:
    inputs:
      reason:
        description: "Reason for running the workflow"
        required: false
        default: "Manual execution"

# Global environment variables accessible in all jobs
env:
  USER: vishal210893
  REPO_NAME: dockerpoc-1

jobs:
  build-artifact:
```

If the rest of the file is also squashed onto one line, the engineer must re-format it manually using the structure laid out in the design spec §5.1 — the workflow has three jobs (`build-artifact`, `docker-build-push`, `update-deployment`) plus a failure-report job. Use `python3 -c "import yaml; print(yaml.dump(yaml.safe_load(open('.github/workflows/build-and-push.yml'))))"` as a re-indent helper if the original is unsalvageable.

- [ ] **Step 3: Update path strings (4× `K8s_Yaml` and 1× `Dockerfile`)**

Apply these four edits to the `update-deployment` job. The exact spellings are:

| Find | Replace with |
|---|---|
| `DEPLOYMENT_FILE="K8s_Yaml/App/Deployment.yaml"` | `DEPLOYMENT_FILE="infra/kubernetes/App/Deployment.yaml"` (two occurrences in the `Patch Kubernetes Deployment YAML` and `Verify Deployment Image Update` steps) |
| `git status --porcelain K8s_Yaml/App/Deployment.yaml` | `git status --porcelain infra/kubernetes/App/Deployment.yaml` |
| `git add K8s_Yaml/App/Deployment.yaml` | `git add infra/kubernetes/App/Deployment.yaml` |
| `git commit -m "chore(deploy): Update Kubernetes deployment image to ${{ env.FULL_DOCKER_IMAGE }}"` | unchanged — commit message text is fine |

And one edit in the `docker-build-push` job:

| Find | Replace with |
|---|---|
| `file: ./Dockerfile` | `file: ./infra/docker/Dockerfile` |

- [ ] **Step 4: YAML parse and grep verify**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('/workspaces/DockerPoc-1/.github/workflows/build-and-push.yml'))" && echo "YAML parses: OK"
grep -nE "K8s_Yaml|file: \./Dockerfile$" /workspaces/DockerPoc-1/.github/workflows/build-and-push.yml && echo "OLD PATHS STILL PRESENT — FAIL" || echo "old paths gone: OK"
grep -nE "infra/kubernetes/App/Deployment.yaml|file: \./infra/docker/Dockerfile" /workspaces/DockerPoc-1/.github/workflows/build-and-push.yml
```

Expected: "YAML parses: OK", "old paths gone: OK", and four-to-five matches for the new paths.

### Task D6: Patch `.github/workflows/helm-release.yml`

**Files:**
- Modify: `.github/workflows/helm-release.yml` line 27

- [ ] **Step 1: Update `charts_dir`**

Old:
```yaml
        with:
          charts_dir: charts
```

New:
```yaml
        with:
          charts_dir: infra/helm
```

- [ ] **Step 2: Verify**

Run:
```bash
grep -nE "charts_dir:" /workspaces/DockerPoc-1/.github/workflows/helm-release.yml
python3 -c "import yaml; yaml.safe_load(open('/workspaces/DockerPoc-1/.github/workflows/helm-release.yml'))" && echo "YAML parses: OK"
```

Expected: `27:          charts_dir: infra/helm` and "YAML parses: OK".

### Task D7: Update `infra/flux/helm-flux.yaml` commented chart reference

**Files:**
- Modify: `infra/flux/helm-flux.yaml` line 17

This is a commented-out line referencing the previous chart path. Update for accuracy.

- [ ] **Step 1: Edit line 17**

Old:
```
#      chart: ./charts/Helm
```

New:
```
#      chart: ./infra/helm/dockerpoc-app
```

- [ ] **Step 2: Verify**

Run:
```bash
grep -n "chart: ./infra/helm/dockerpoc-app" /workspaces/DockerPoc-1/infra/flux/helm-flux.yaml
```

Expected: one match.

### Task D8: Patch `Readme.md` and rename to `README.md`

**Files:**
- Modify then rename: `Readme.md` → `README.md`

There are **nine** path mentions in `Readme.md` that need updating (more than the spec's initial estimate of six). The full list, with current line numbers:

| Line | Old text | New text |
|---|---|---|
| 57 | `Dockerfile` in the root directory. | `Dockerfile` at `infra/docker/Dockerfile`. |
| 71 | located in the `K8s_Yaml` directory. | located in the `infra/kubernetes` directory. |
| 76 | `kubectl apply -f K8s_Yaml/` | `kubectl apply -f infra/kubernetes/` |
| 79 | in the `K8s_Yaml` directory | in the `infra/kubernetes` directory |
| 94 | located in the `charts/Helm` directory. | located in the `infra/helm/dockerpoc-app` directory. |
| 105 | Navigate to the `charts/Helm` directory | Navigate to the `infra/helm/dockerpoc-app` directory |
| 108 | `cd charts/Helm` | `cd infra/helm/dockerpoc-app` |
| 125 | located in the `Terraform` directory. | located in the `infra/terraform` directory. |
| 130 | `cd Terraform` | `cd infra/terraform` |

- [ ] **Step 1: Apply all nine path edits in `Readme.md`**

Use straightforward find-and-replace per row above. The replacements are unambiguous because each search string appears exactly once.

- [ ] **Step 2: Verify path edits**

Run:
```bash
grep -nE "K8s_Yaml|charts/Helm|cd Terraform|Terraform. directory|Dockerfile.* in the root" /workspaces/DockerPoc-1/Readme.md && echo "OLD PATHS STILL PRESENT — FAIL" || echo "all old paths gone: OK"
grep -nE "infra/kubernetes|infra/helm/dockerpoc-app|infra/terraform|infra/docker/Dockerfile" /workspaces/DockerPoc-1/Readme.md | wc -l
```

Expected: "all old paths gone: OK" and a count of at least 9.

- [ ] **Step 3: Rename to `README.md`**

Run:
```bash
git -C /workspaces/DockerPoc-1 mv Readme.md README.md
```

- [ ] **Step 4: Verify**

Run:
```bash
test -f /workspaces/DockerPoc-1/README.md && echo "README.md present: OK"
test ! -f /workspaces/DockerPoc-1/Readme.md && echo "old Readme.md gone: OK"
```

### Task D9: Verify other docs do NOT need edits

**Files:** read-only verification.

The design spec called out `docs/helm-terraform.md` and `docs/remote-debugging.md` as targets for cosmetic edits. On closer reading (see plan preamble), neither requires changes:
- `docs/helm-terraform.md` line 42's `└── charts/  # Dependencies` refers to the Helm chart's own internal `charts/` subdirectory for dependencies — not the repo's old `charts/` path.
- `docs/remote-debugging.md` contains zero occurrences of "Debbuging" in its body (the typo was filename-only and is fixed by the rename in Task C7).

- [ ] **Step 1: Confirm no edits needed**

Run:
```bash
grep -nE "K8s_Yaml|^Terraform/|^charts/Helm" /workspaces/DockerPoc-1/docs/helm-terraform.md /workspaces/DockerPoc-1/docs/remote-debugging.md && echo "edits needed — investigate" || echo "no edits needed: OK"
grep -n "Debbuging" /workspaces/DockerPoc-1/docs/remote-debugging.md && echo "Debbuging in body — fix" || echo "no in-body typo: OK"
```

Expected: both "OK" lines print, no `grep` matches.

---

## Phase E — Verification and commit

### Task E1: Maven build

**Files:** none (build only).

- [ ] **Step 1: Run Maven build**

Run:
```bash
cd /workspaces/DockerPoc-1 && mvn -B -q install 2>&1 | tail -40
```

Expected: build succeeds (no `BUILD FAILURE`). The Maven build is independent of the directory reorg (the JAR is built from `src/`, output to `target/`), so it should pass unconditionally if the toolchain is set up.

- [ ] **Step 2: Verify JAR exists**

Run:
```bash
ls -la /workspaces/DockerPoc-1/target/dockerpoc-1.jar
```

Expected: file present, size > 0.

If Maven is not installed inside the devcontainer, document the skip and proceed. The CI pipeline will catch any build regression on push. **Do not** mark this step "passed" without confirming the JAR exists.

### Task E2: Docker build

**Files:** none (build only).

- [ ] **Step 1: Build the image from the new Dockerfile location**

Run:
```bash
cd /workspaces/DockerPoc-1 && docker build -f infra/docker/Dockerfile -t dockerpoc:reorg-check . 2>&1 | tail -30
```

Expected: `Successfully tagged dockerpoc:reorg-check` (or moral equivalent in modern buildx output). If the Docker daemon is not reachable from inside the devcontainer, document the skip — CI will exercise this on push.

- [ ] **Step 2: Clean up the image**

Run:
```bash
docker rmi dockerpoc:reorg-check 2>/dev/null || true
```

### Task E3: Helm lint and template

**Files:** none.

- [ ] **Step 1: Helm lint**

Run:
```bash
helm lint /workspaces/DockerPoc-1/infra/helm/dockerpoc-app 2>&1 | tail -20
```

Expected: `1 chart(s) linted, 0 chart(s) failed`. Warnings are acceptable.

- [ ] **Step 2: Helm template**

Run:
```bash
helm template dockerpoc /workspaces/DockerPoc-1/infra/helm/dockerpoc-app 2>&1 | head -20
```

Expected: YAML output begins (Deployments / Services etc. render). No `Error:` lines.

### Task E4: `Deploy.sh` path constants resolve

**Files:** none.

- [ ] **Step 1: Bash syntax check**

Run:
```bash
bash -n /workspaces/DockerPoc-1/Deploy.sh && echo "syntax OK"
```

Expected: `syntax OK`.

- [ ] **Step 2: Path constants resolve to real files / directories**

Run:
```bash
cd /workspaces/DockerPoc-1 && bash -c '
  set -e
  SCRIPT_DIR="$(pwd)"
  source <(sed -n "1,30p" Deploy.sh)
  [[ -f "$K8S_APP_MANIFEST_FILE" ]] || { echo "MISSING: $K8S_APP_MANIFEST_FILE"; exit 1; }
  [[ -d "$K8S_INGRESS_MANIFEST_PATH" ]] || { echo "MISSING: $K8S_INGRESS_MANIFEST_PATH"; exit 1; }
  echo "Deploy.sh paths resolve: OK"
'
```

Expected: `Deploy.sh paths resolve: OK`.

### Task E5: Workflow YAML parses

**Files:** none.

- [ ] **Step 1: Parse both workflow files**

Run:
```bash
python3 -c "import yaml, sys
for f in ['/workspaces/DockerPoc-1/.github/workflows/build-and-push.yml',
          '/workspaces/DockerPoc-1/.github/workflows/helm-release.yml']:
    yaml.safe_load(open(f))
    print(f'OK: {f}')"
```

Expected: two `OK:` lines, no exceptions.

### Task E6: Path grep sweep

**Files:** none — scan the whole tree for any lingering references to old paths.

- [ ] **Step 1: Grep for old paths**

Run:
```bash
grep -RnE 'K8s_Yaml|charts/Helm|^nginx/|^Terraform/|clusters/flux' \
  --exclude-dir=.git \
  --exclude-dir=target \
  --exclude-dir=node_modules \
  --exclude-dir=.idea \
  /workspaces/DockerPoc-1 | grep -vE '/(docs/superpowers/|infra/flux/Doc/)' | head -40
```

Expected: empty output. Hits inside `docs/superpowers/` (this plan and the design spec) and `infra/flux/Doc/*.md` (aspirational FluxCD layout docs) are excluded.

If non-excluded hits appear, fix them before moving on. They indicate a missed call-site.

### Task E7: Stage everything, review diff, single commit

**Files:** all of the above.

- [ ] **Step 1: Stage any not-yet-staged edits**

Most file moves are auto-staged by `git mv`. File-content edits (`.gitignore`, `Deploy.sh`, `Makefile`, workflows, Dockerfile, docker-compose, `infra/flux/helm-flux.yaml`, the renamed `README.md`) need explicit staging:

```bash
git -C /workspaces/DockerPoc-1 add \
  .gitignore \
  Deploy.sh \
  Makefile \
  README.md \
  .github/workflows/build-and-push.yml \
  .github/workflows/helm-release.yml \
  infra/docker/Dockerfile \
  infra/docker/docker-compose.yml \
  infra/flux/helm-flux.yaml \
  docs/superpowers/specs/2026-05-21-repo-reorganization-design.md \
  docs/superpowers/plans/2026-05-21-repo-reorganization.md
```

- [ ] **Step 2: Review the staged diff summary**

Run:
```bash
git -C /workspaces/DockerPoc-1 status --short
git -C /workspaces/DockerPoc-1 diff --cached --stat | tail -30
```

Expected highlights:
- `R` (rename) lines for all `git mv` operations.
- `D` (delete) lines for the untracked `node_modules` files and `docker-poc-1.iml`, `test`.
- `M` (modify) for `.gitignore`, `Deploy.sh`, `Makefile`, workflows, Dockerfile, etc.
- `A` (add) for the two new spec / plan markdown files under `docs/superpowers/`.
- Working tree should be effectively empty (`git status --short` shows nothing un-staged, or only intentional out-of-scope items like the running session's `.claude/` directory if those are still untracked).

- [ ] **Step 3: Inspect a few key diffs by hand**

Run:
```bash
git -C /workspaces/DockerPoc-1 diff --cached -- .gitignore | head -40
git -C /workspaces/DockerPoc-1 diff --cached -- Deploy.sh
git -C /workspaces/DockerPoc-1 diff --cached -- .github/workflows/build-and-push.yml | head -60
git -C /workspaces/DockerPoc-1 diff --cached -- README.md | head -40
```

Read each and confirm it matches the intent of the corresponding Task D step.

- [ ] **Step 4: Final pre-commit confidence check**

Run:
```bash
git -C /workspaces/DockerPoc-1 ls-files | wc -l
```

Expected: roughly `pre-count − 389 (node_modules) − 1 (iml) − 1 (test) + 2 (new spec/plan docs)` ≈ pre-count − 389. If the number is significantly off, investigate.

- [ ] **Step 5: Commit**

Run:
```bash
git -C /workspaces/DockerPoc-1 commit -m "$(cat <<'EOF'
refactor(repo): regroup deploy artefacts under infra/, normalize layout

- Move K8s manifests to infra/kubernetes/
- Move Helm chart to infra/helm/dockerpoc-app/ (matches Chart.yaml name)
- Move Terraform to infra/terraform/
- Move Flux manifests to infra/flux/
- Move Dockerfiles, docker-compose, nginx/ to infra/docker/
- Move loose docs to docs/
- Rename Readme.md → README.md; RemoteDebbuging.md → docs/remote-debugging.md
- Rewrite .gitignore (keep .idea/ tracked per maintainer preference)
- Untrack vendored node_modules, docker-poc-1.iml, stray test file
- Update CI workflows, Makefile, Deploy.sh, Dockerfile to new paths
- Fix line-1 corruption in .github/workflows/build-and-push.yml

Design: docs/superpowers/specs/2026-05-21-repo-reorganization-design.md
Plan:   docs/superpowers/plans/2026-05-21-repo-reorganization.md
EOF
)"
```

Expected: `[learning/github-action <new-sha>] refactor(repo): …` plus a file-count summary. Working tree clean afterwards.

- [ ] **Step 6: Post-commit sanity**

Run:
```bash
git -C /workspaces/DockerPoc-1 log --oneline -1
git -C /workspaces/DockerPoc-1 show --stat HEAD | head -5
git -C /workspaces/DockerPoc-1 status
```

Expected: new commit at the top of `learning/github-action`, working tree clean.

---

## Self-Review

Run after writing — this is the plan author's own check before handoff.

**Spec coverage** (mapping every spec section to a task):

| Spec section | Plan task(s) |
|---|---|
| §1 Goal / scope | covered by the whole plan |
| §2 Target layout | Phase A2 (skeleton) + Phase C (moves) |
| §3.1 Directory moves | C1–C5 |
| §3.2 File moves and renames | C6, C7, D8 (rename README) |
| §3.3 Untrack | B1 |
| §4 .gitignore | A3 |
| §5.1 build-and-push.yml | D5 |
| §5.2 helm-release.yml | D6 |
| §5.3 Deploy.sh | D3 |
| §5.4 Makefile | D4 |
| §5.5 Dockerfile + maven-build + compose | D1, D2 (Dockerfile.maven-build has no internal path refs — flagged in plan preamble, no task needed) |
| §5.6 helm-flux.yaml comment | D7 |
| §5.7 Docs cosmetic edits | D8 (Readme), D9 (verify others need no edits) |
| §6 Execution order | Phases A→B→C→D→E ordered per spec |
| §7 Verification | E1–E6 |
| §8 Risks | acknowledged in plan preamble |
| §9 Branch strategy | committed directly on `learning/github-action` in E7 |
| §10 Out-of-band state | unchanged (stash@{0} still recoverable) |

**Placeholder scan:** Every step has either a concrete command or a concrete code block. No "TBD" / "TODO" / "implement later" / "add appropriate X" patterns.

**Type / name consistency:** Every directory name (`infra/kubernetes`, `infra/helm/dockerpoc-app`, `infra/terraform`, `infra/flux`, `infra/docker/nginx`) is spelled identically across all tasks and verifications. The chart name `dockerpoc-app` matches both the new directory name and `Chart.yaml`'s `name:` field.

**Open question for the engineer running this:**
- `Dockerfile.maven-build` (`infra/docker/Dockerfile.maven-build`) does `COPY ./ ./` from the build context. If anyone actually builds this image after the reorg, they will need to invoke `docker build -f infra/docker/Dockerfile.maven-build .` from repo root — the same context-anchoring rule as the main `Dockerfile`. There is no script that builds it today, so the plan does not patch any caller. Worth flagging in the commit message or a follow-up.
