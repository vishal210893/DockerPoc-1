# DockerPoc-1

*A personal endeavor to learn and demonstrate various DevOps concepts: Docker, Kubernetes, Helm, and Terraform — all wrapped around a Spring Boot application.*

---

## ✨ Project Highlights

* **Spring Boot Application:** A simple RESTful API built with Spring Boot, showcasing basic CRUD operations and exposing monitoring endpoints.
* **Docker:** Containerization of the Spring Boot application for consistent environments.
* **Kubernetes:** Deployment and management of the application on a Kubernetes cluster using various manifest types (Deployments, Services, etc.).
* **Helm:** Packaging and deploying the application to Kubernetes using Helm charts for easier management and versioning.
* **Terraform:** Infrastructure as Code (IaC) for provisioning and managing the necessary cloud resources (current state focuses on local configurations, but the structure is in place for cloud integration).
* **CI/CD:** *(Implied/Expandable)* The project structure allows for the integration of CI/CD pipelines to automate builds, tests, and deployments.

---

## 📁 Repository Layout

Deployment artefacts live under `infra/` and reference documentation lives under `docs/`. Top-level files (`Deploy.sh`, `Makefile`, `pom.xml`) stay where they are because they're entry points.

```
DockerPoc-1/
├── src/                            Spring Boot Java source
├── infra/
│   ├── docker/                     Dockerfile, Dockerfile.maven-build, nginx/, docker-compose.yml
│   ├── kubernetes/                 K8s manifests (App/, Ingress/, Storage/, …)
│   ├── helm/dockerpoc-app/         Helm chart — directory matches Chart.yaml `name:`
│   ├── terraform/                  Terraform .tf files and state
│   └── flux/                       FluxCD manifests and docs
├── docs/                           Standalone Markdown docs (helm-terraform, remote-debugging, …)
├── .github/workflows/              CI: build-and-push.yml, helm-release.yml
├── Deploy.sh                       Build + deploy helper script
├── Makefile                        Convenience targets (see below)
├── pom.xml                         Maven build
└── README.md
```

---

## 🚀 Getting Started

### 🔧 Prerequisites

Ensure the following are installed before you begin:

* JDK 17
* Maven
* Docker
* `kubectl`
* Helm
* Terraform
* A running Kubernetes cluster (Minikube, Docker Desktop with Kubernetes enabled, or a cloud-based cluster)

### 📦 Cloning the Repository

```bash
git clone https://github.com/your-username/DockerPoc-1.git
cd DockerPoc-1
```

### 🏗️ Building the Spring Boot Application

```bash
mvn clean package
```

This will create an executable JAR file in the `target` directory.

---

## 🛠️ Make Targets

Common workflows wrapped as Make targets. All commands assume you run them from the repo root.

| Target | What it does |
|---|---|
| `make build` | Runs `./Deploy.sh --build-only` — Maven build, Docker build & push, patches K8s manifest **and** Helm chart `values.yaml` tag. **No deploy.** |
| `make deploy` | Recreates the local k3d cluster, then runs `./Deploy.sh --build` (full pipeline including kubectl apply + port-forward). |
| `make run` | Runs `./Deploy.sh` — skips build, just applies the current K8s manifests and port-forwards. |
| `make delete` | `kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found`. |
| `make helm-install` | `helm install dockerpoc ./infra/helm/dockerpoc-app`. |
| `make helm-delete` | `helm delete dockerpoc` (ignores absent release). |
| `make helm-redeploy` | helm delete + helm install + port-forward in one shot. |
| `make helm-package` | `helm lint` + `helm package` → `dist/dockerpoc-app-<version>.tgz` (`dist/` is gitignored). |
| `make port-forward` | `kubectl port-forward svc/ingress-nginx-controller 8005:80 -n ingress-nginx`. |

### Deploy.sh modes

`Deploy.sh` accepts an optional flag:

```bash
./Deploy.sh                 # Steps 4-6 only: kubectl apply + port-forward (no build)
./Deploy.sh --build         # Steps 1-3b then 4-6: full build + push + patch + deploy
./Deploy.sh --build-only    # Steps 1-3b only: build + push + patch — no kubectl
```

Step 3 patches the image tag in `infra/kubernetes/App/Deployment.yaml`; Step 3b patches `tag:` in `infra/helm/dockerpoc-app/values.yaml`. After `make build` you can `make helm-install` and the chart will pull the freshly-built image.

---

## 🐳 Docker Deployment

1. **Build the Docker Image:**

   ```bash
   docker build -f infra/docker/Dockerfile -t dockerpoc-1 .
   ```

   The build context stays at the repo root so the Dockerfile's `COPY target/dockerpoc-1.jar` and `COPY infra/docker/nginx/` paths resolve correctly.

2. **Run the Docker Container:**

   ```bash
   docker run -p 8005:8005 \
     -e SPRING_PROFILES_ACTIVE=dev \
     dockerpoc-1
   ```

   The app listens on port 8005 inside the container (`server.port` in `application.yml`). The `dev` profile uses CONSOLE-only logging and the in-memory H2 datasource; switch to `prod` for the Aiven Postgres datasource and rolling file logs.

---

## ☸️ Kubernetes Deployment

Kubernetes manifests are located in the `infra/kubernetes` directory.

1. **Apply the Manifests:**

   ```bash
   kubectl apply -f infra/kubernetes/
   ```

   This command applies all the Kubernetes configuration files in the `infra/kubernetes` directory to your connected Kubernetes cluster.

2. **Verify the Deployment:**

   ```bash
   kubectl get pods
   kubectl get services
   ```

   Check the status of your pods and services to ensure the application is running correctly.

---

## 📈 Helm Chart Deployment

The Helm chart for this project is located in the `infra/helm/dockerpoc-app` directory.

1. **Add the Helm Repository (if hosted):**
   If you have hosted your Helm chart in a repository, you can add it using:

   ```bash
   helm repo add my-repo https://your-helm-repo-url
   helm repo update
   ```

2. **Install the Helm Chart:**

   ```bash
   make helm-install                     # or: helm install dockerpoc ./infra/helm/dockerpoc-app
   ```

   The chart is installed under release name `dockerpoc`. After a `make build` run the chart's image tag has already been patched, so it pulls the freshly-pushed image.

3. **Package the chart for distribution:**

   ```bash
   make helm-package
   ```

   This lints the chart and writes `dist/dockerpoc-app-<version>.tgz`. The `dist/` directory is gitignored.

4. **Uninstall the Helm Chart:**

   ```bash
   make helm-delete                      # or: helm uninstall dockerpoc
   ```

---

## 🏗️ Terraform Configuration

Terraform files are located in the `infra/terraform` directory. While the current configuration might be minimal, the intention is to use Terraform for provisioning infrastructure.

1. **Initialize Terraform:**

   ```bash
   cd infra/terraform
   terraform init
   ```

2. **Review the Plan:**

   ```bash
   terraform plan
   ```

   This command shows you the changes Terraform will make to your infrastructure.

3. **Apply the Configuration:**

   ```bash
   terraform apply
   ```

   This command applies the Terraform configuration to provision the resources.

---

## 📜 Logging & Spring Profiles

Logging is driven by `src/main/resources/logback-spring.xml` (Spring Boot–aware naming so `<springProfile>` blocks activate). Behaviour by profile:

| Profile | Appenders attached to root + `com.learning.docker` |
|---|---|
| `dev` | CONSOLE only (coloured pattern, no file written) |
| `prod` | CONSOLE + rolling FILE → `logs/dockerpoc.log`, daily rollover, 100 MB/file, 30-day retention, 1 GB cap |
| other (`default`, `test`, …) | No custom root logger; Spring Boot's default config applies |

Set the profile via:

```bash
java -Dspring.profiles.active=prod -Ddb.password=<password> -jar target/dockerpoc-1.jar
# or, in container env vars: SPRING_PROFILES_ACTIVE=prod
```

The `Dockerfile` bakes `-Dspring.profiles.active=prod` into `JAVA_OPTS`, so images built via `make build` default to prod logging + Postgres.

---

## 🔍 Exploring the Application

Once the application is deployed, you can typically access it through a Service (Kubernetes) or by port-forwarding to a pod. Refer to the specific deployment method's documentation for details.

* **API Endpoints:** The Spring Boot application exposes various REST endpoints.
* **Actuator Endpoints:** Monitoring information is available via Spring Boot Actuator (e.g., `/actuator/health`, `/actuator/metrics`).
* **Swagger UI:** API documentation is available through Swagger UI (commonly `/swagger-ui.html`, depending on your configuration).

---

## 📑 Further Documentation

Deeper operator guides live under [`docs/`](docs/):

- [**docs/build-and-deploy.md**](docs/build-and-deploy.md) — pipeline internals (Deploy.sh stages, image tag synchronisation, Make targets, Helm flows, profile-aware logging, troubleshooting).
- [**docs/push-chart-to-local-chartmuseum.md**](docs/push-chart-to-local-chartmuseum.md) — 5-minute local-dev setup for pushing this chart to a basic-auth-protected ChartMuseum on your Mac (used to exercise KubeVela's HTTPS chart-fetch auth path).
- [**docs/helm-terraform.md**](docs/helm-terraform.md) — reference for combining Helm with Terraform-managed infrastructure.
- [**docs/remote-debugging.md**](docs/remote-debugging.md) — JDWP remote debugging walkthrough into a pod from IntelliJ.

---

## 📚 Learning and Exploration

This project is designed as a learning tool. Feel free to explore the code and configurations to understand how the different technologies are integrated. Experiment with different deployment strategies, modify the application, and observe the impact on the deployment process.

---

## 🤝 Contributing

As this is a personal learning project, contributions are not actively sought, but you are welcome to fork the repository and use it for your own learning purposes.

---

## 📄 License

This project is licensed under the **MIT License**.

---

## 🙏 Acknowledgments

Thanks to the open-source communities of Spring Boot, Docker, Kubernetes, Helm, and Terraform for providing these incredible tools.
