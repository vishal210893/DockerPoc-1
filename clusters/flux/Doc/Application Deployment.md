# 🚀 FluxCD GitOps Implementation Guide

## 📖 Table of Contents
- [🔍 Understanding FluxCD Architecture](#-understanding-fluxcd-architecture)
- [🔐 Authentication & Security](#-authentication--security)
- [🎯 Working with GitRepositories](#-working-with-gitrepositories)
- [⚙️ Kustomization Resources](#️-kustomization-resources)
- [🧪 Hands-on Lab: Deploying PodInfo](#-hands-on-lab-deploying-podinfo)
- [📝 Key Concepts & Best Practices](#-key-concepts--best-practices)

---

## 🔍 Understanding FluxCD Architecture

### 🤔 How FluxCD Works
FluxCD doesn't run as a single pod but operates through multiple controllers that continuously watch and apply changes from Git repositories to your Kubernetes cluster. This distributed architecture ensures high availability and efficient resource management.

### 🔧 Custom Resource Definitions (CRDs)
When FluxCD is installed, it creates several CRDs, with **GitRepository** being one of the most important:

```bash
# Check existing GitRepository resources
kubectl get gitrepo -n flux-system
```

**Sample Output:**
```
NAME           URL                                    READY   STATUS
flux-system    https://gitlab.com/your-repo/flux-cd   True    Fetched revision: main/abc123
```

### 📊 GitRepository Resource Structure
- **URL**: Points to your Git repository location
- **Status**: Contains the latest commit hash from the monitored branch
- **Branch**: Defines which branch FluxCD monitors (default: main)
- **Sync Interval**: How frequently FluxCD checks for changes

> 💡 **Pro Tip**: You can modify the monitored branch by editing the GitRepository resource, but it's recommended to use different repositories for different environments.

---

## 🔐 Authentication & Security

### 🔑 Private Repository Access
FluxCD uses Kubernetes secrets to authenticate with private repositories. The personal access token created during bootstrap is stored securely:

```bash
# List secrets in flux-system namespace
kubectl get secret -n flux-system

# Decode the GitLab token (for debugging purposes only)
kubectl get secret flux-system -n flux-system -o jsonpath='{.data.password}' | base64 -d
```

> ⚠️ **Security Warning**: Never expose these secrets! Only cluster administrators should have access to the flux-system namespace.

### 🛡️ RBAC Configuration
FluxCD requires specific permissions to function properly:

```bash
# Check FluxCD cluster role bindings
kubectl get clusterrolebindings | grep flux
```

**Key Roles:**
- **cluster-reconciler**: Has cluster-admin privileges to apply manifests
- **CRD handler**: Manages FluxCD custom resources with custom permissions

---

## 🎯 Working with GitRepositories

### 📋 Creating a GitRepository Resource
To make FluxCD aware of additional repositories, create a GitRepository resource:

**File: `podinfo-repo.yaml`**
```yaml
apiVersion: source.toolkit.fluxcd.io/v1
kind: GitRepository
metadata:
  name: podinfo
  namespace: flux-system  # ⚠️ Must be flux-system, not target namespace
spec:
  interval: 30s  # Sync frequency
  ref:
    branch: master  # Monitored branch
  url: https://github.com/stefanprodan/podinfo
```

### 🎯 Key Configuration Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `interval` | How often FluxCD checks for changes | `30s`, `5m`, `1h` |
| `ref.branch` | Git branch to monitor | `main`, `master`, `develop` |
| `url` | Repository URL | GitHub, GitLab, Bitbucket URLs |

> 📝 **Common Mistake**: Don't use the target application namespace in GitRepository metadata. Always use `flux-system`.

---

## ⚙️ Kustomization Resources

### 🔄 Understanding Kustomization
While GitRepository tells FluxCD *what* to watch, Kustomization tells it *what to do* when changes are detected.

**File: `podinfo-kustomization.yaml`**
```yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: podinfo
  namespace: flux-system  # Configuration lives in flux-system
spec:
  interval: 5m0s  # Reconciliation frequency
  path: ./kustomize  # Path within the repository
  prune: true  # Enable garbage collection
  sourceRef:
    kind: GitRepository
    name: podinfo  # References the GitRepository resource
  targetNamespace: default  # Where the application will be deployed
```

### 🔧 Kustomization Spec Breakdown

#### ⏰ Interval
- **Purpose**: Defines reconciliation frequency
- **Function**: How often FluxCD ensures cluster state matches Git state
- **Example**: If someone manually changes replica count, FluxCD will revert it back every interval

#### 📁 Path
- **Purpose**: Specifies directory containing manifests
- **Flexibility**: Can point to subdirectories, multiple paths
- **Best Practice**: Use descriptive paths like `./apps/production` or `./infrastructure/base`

#### 🗑️ Prune
- **Enabled**: Resources deleted from Git are deleted from cluster
- **Disabled**: Manual cleanup required for removed resources
- **Recommendation**: Enable for development, consider carefully for production

#### 🎯 Target Namespace
- **Function**: Where application resources will be created
- **Flexibility**: Can be different from flux-system
- **Important**: This is the ONLY place where you specify the application namespace

---

## 🧪 Hands-on Lab: Deploying PodInfo

### 📚 About PodInfo
PodInfo is a popular cloud-native application perfect for testing GitOps workflows. It displays information about the serving pod and includes built-in health checks and metrics.

### 🔗 Repository Structure
Navigate to: https://github.com/stefanprodan/podinfo

The `kustomize` directory contains:
- `deployment.yaml` - Pod specification
- `service.yaml` - Service configuration
- `hpa.yaml` - Horizontal Pod Autoscaler
- `kustomization.yaml` - Kustomize configuration

### 🚀 Step-by-Step Deployment

#### Step 1: Create GitRepository Resource
```bash
# Create the GitRepository configuration
cat > podinfo-repo.yaml << EOF
apiVersion: source.toolkit.fluxcd.io/v1
kind: GitRepository
metadata:
  name: podinfo
  namespace: flux-system
spec:
  interval: 30s
  ref:
    branch: master
  url: https://github.com/stefanprodan/podinfo
EOF
```

#### Step 2: Create Kustomization Resource
```bash
# Create the Kustomization configuration
cat > podinfo-kustomization.yaml << EOF
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: podinfo
  namespace: flux-system
spec:
  interval: 5m0s
  path: ./kustomize
  prune: true
  sourceRef:
    kind: GitRepository
    name: podinfo
  targetNamespace: default
EOF
```

#### Step 3: Commit and Push Changes
```bash
# Add all changes to staging
git add -A

# Commit with descriptive message
git commit -m "Adds the podinfo repo and kustomization"

# Handle any upstream changes (if needed)
git pull --rebase

# Push to remote repository
git push
```

#### Step 4: Monitor Deployment
```bash
# Watch the synchronization process
flux get kustomizations --watch

# Alternative: Check kustomization status
kubectl get kustomizations -n flux-system
```

**Expected Output:**
```
NAME      READY   STATUS                        AGE
podinfo   True    Applied revision: master...   30s
```

#### Step 5: Verify Deployment
```bash
# Check if pods are running
kubectl get pods

# Check deployment status
kubectl get deployment podinfo

# Check service
kubectl get service podinfo
```

#### Step 6: Access the Application
```bash
# Port forward to access the application
kubectl port-forward deployment/podinfo 9898:9898 --address 0.0.0.0
```

Open your browser and navigate to: `http://your-server-ip:9898`

---

## 📝 Key Concepts & Best Practices

### ✅ GitOps Benefits Over kubectl apply

| Traditional Approach | GitOps with FluxCD |
|---------------------|-------------------|
| Manual deployment steps | Declarative, automated |
| No audit trail | Full Git history |
| Configuration drift | Self-healing |
| Manual rollbacks | Git-based rollbacks |
| Limited collaboration | Git-based workflows |

### 🎯 Best Practices

#### 🏗️ Repository Structure
```
flux-config/
├── clusters/
│   ├── production/
│   └── staging/
├── apps/
│   ├── podinfo/
│   └── nginx/
└── infrastructure/
    ├── ingress/
    └── monitoring/
```

#### 🔒 Security Considerations
- ✅ Use separate repositories for different environments
- ✅ Implement proper RBAC for flux-system namespace
- ✅ Rotate access tokens regularly
- ✅ Use sealed secrets for sensitive data
- ❌ Never commit secrets directly to Git

#### 🔄 Workflow Recommendations
- **Development**: Short sync intervals (30s-1m)
- **Production**: Longer intervals (5m-15m) for stability
- **Enable pruning**: For development environments
- **Disable pruning**: For production (manual cleanup)

### 🐛 Common Troubleshooting

#### Issue: Resources Stuck in Pending
```bash
# Check kustomization status
kubectl describe kustomization podinfo -n flux-system

# Common cause: Wrong namespace in metadata
# Solution: Ensure GitRepository and Kustomization use flux-system namespace
```

#### Issue: Authentication Failures
```bash
# Check if secret exists and is valid
kubectl get secret flux-system -n flux-system

# Verify GitRepository status
kubectl describe gitrepository podinfo -n flux-system
```

#### Issue: Changes Not Syncing
```bash
# Force reconciliation
flux reconcile kustomization podinfo

# Check sync status
flux get kustomizations
```

### 📊 Monitoring FluxCD

```bash
# Monitor all FluxCD resources
flux get all

# Check specific resource types
flux get sources git
flux get kustomizations
flux get helmreleases

# Suspend/resume resources
flux suspend kustomization podinfo
flux resume kustomization podinfo
```

---

## 🎓 What's Next?

This lab covered the fundamentals of FluxCD GitOps workflow. Future topics to explore:

- 🔄 **Multi-environment workflows** with different branches
- 🎡 **Helm integration** for complex applications
- 🔐 **Sealed Secrets** for sensitive data management
- 📊 **Monitoring and alerting** for GitOps workflows
- 🏗️ **Progressive delivery** with Flagger
- 🔧 **Advanced Kustomize patterns** and overlays

> 💡 **Remember**: The initial setup might seem complex compared to `kubectl apply`, but the long-term benefits of GitOps become evident as your infrastructure grows and team collaboration increases!

---

*📚 Continue your FluxCD journey by exploring advanced features and real-world scenarios!*