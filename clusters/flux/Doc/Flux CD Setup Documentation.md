# 📖 Flux CD Setup Documentation

This documentation provides detailed instructions for installing Flux CD on a local machine and integrating it with a GitHub repository.

---

## 🚀 Installation of Flux CD

Flux CD is a GitOps tool that ensures the state of your Kubernetes clusters matches the configurations stored in your Git repositories. It supports major Git providers like GitHub, GitLab, and Bitbucket.

### Prerequisites

* Kubernetes version **1.23 or later**
* Homebrew (Linux or macOS) or Chocolatey (Windows)

### Install Flux CD

Run the following command to install Flux CD using Homebrew:

```bash
brew install fluxcd/tap/flux
```

For alternative methods (e.g., Windows), refer to [Flux CD Installation](https://fluxcd.io/flux/installation/).

---

## 📂 Directory Structure Setup

Create the necessary directory structure in your Git repository to store Flux CD configurations:

```bash
cd myfluxrepo
mkdir -p clusters/my-cluster/flux-system
cd clusters/my-cluster/flux-system
```

> **Note:** The name `my-cluster` is a Flux configuration identifier, not the actual cluster name.

Create empty configuration files:

```bash
touch gotk-components.yaml gotk-sync.yaml kustomization.yaml
```

### Configure `kustomization.yaml`

Update `kustomization.yaml` with the following content:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - gotk-components.yaml
  - gotk-sync.yaml
```

---

## 🔑 GitHub Access Token

Flux CD requires access to your GitHub repository. Create a personal access token:

* **Navigate to:** GitHub > Settings > Developer settings > Personal access tokens
* **Token Name:** Flux (or any descriptive name)
* **Permissions:** Select appropriate permissions such as `repo`
* **Generate and copy the token value** (store safely)

Export the token as an environment variable:

```bash
export GITHUB_TOKEN=<your_token_value>
```

---

## 🔄 Bootstrap Flux CD with GitHub

Run the Flux CD bootstrap command to initialize the repository and configure Flux CD components:

```bash
flux bootstrap github \
  --owner=abohmeed \
  --repository=myfluxrepo \
  --branch=main \
  --path=clusters/flux \
  --token-auth \
  --personal
```

### What Happens During Bootstrap:

* Flux connects to your GitHub repository.
* Flux populates the previously empty configuration files.
* Flux commits and pushes these files back to your GitHub repository.
* Flux installs its components (Helm, Kustomize, Notification, Source controllers) onto the Kubernetes cluster.

---

## ✅ Verification

Verify that Flux CD has been successfully installed:

```bash
kubectl get pods -n flux-system
```

You should see four pods:

* **Helm Controller**
* **Kustomize Controller**
* **Notification Controller**
* **Source Controller**

> **Note:** Unlike Argo CD, Flux CD doesn't create a pod explicitly named `flux`; instead, it deploys separate controllers for managing cluster states.

---

## ♻️ Managing Multiple Clusters

Flux CD supports managing multiple clusters using the same Git repository configuration:

* Simply rerun the bootstrap command pointing to the same repository and path for additional clusters.

---

## 📚 Additional Information

* Flux CD stores its configuration in a Git repository, making it easy to track changes.
* The personal access token must be periodically renewed; set reminders accordingly.
* Flux CD supports GitHub, GitLab, Bitbucket, and other git-hosted services natively.

---

## 🛠️ Troubleshooting

* **Token Expiry:** Ensure tokens are valid and renewed to avoid authentication issues.
* **Pods Verification:** Regularly verify pods' status in the `flux-system` namespace.

---

This concludes the documentation on setting up Flux CD locally and integrating it with GitHub. Refer to [Flux Documentation](https://fluxcd.io/docs/) for more advanced configurations and use-cases.
