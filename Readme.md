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

## 🐳 Docker Deployment

1. **Build the Docker Image:**

   ```bash
   docker build -t dockerpoc-1 .
   ```

   This command builds a Docker image named `dockerpoc-1` using the `Dockerfile` in the root directory.

2. **Run the Docker Container:**

   ```bash
   docker run -p 8080:8080 dockerpoc-1
   ```

   This runs the Docker container and maps port 8080 on your host to port 8080 in the container.

---

## ☸️ Kubernetes Deployment

Kubernetes manifests are located in the `K8s_Yaml` directory.

1. **Apply the Manifests:**

   ```bash
   kubectl apply -f K8s_Yaml/
   ```

   This command applies all the Kubernetes configuration files in the `K8s_Yaml` directory to your connected Kubernetes cluster.

2. **Verify the Deployment:**

   ```bash
   kubectl get pods
   kubectl get services
   ```

   Check the status of your pods and services to ensure the application is running correctly.

---

## 📈 Helm Chart Deployment

The Helm chart for this project is located in the `charts/Helm` directory.

1. **Add the Helm Repository (if hosted):**
   If you have hosted your Helm chart in a repository, you can add it using:

   ```bash
   helm repo add my-repo https://your-helm-repo-url
   helm repo update
   ```

2. **Install the Helm Chart:**
   Navigate to the `charts/Helm` directory and install the chart:

   ```bash
   cd charts/Helm
   helm install dockerpoc-release .
   ```

   This will deploy the application to your Kubernetes cluster using the configurations defined in the Helm chart.

3. **Uninstall the Helm Chart:**
   To remove the deployment created by Helm:

   ```bash
   helm uninstall dockerpoc-release
   ```

---

## 🏗️ Terraform Configuration

Terraform files are located in the `Terraform` directory. While the current configuration might be minimal, the intention is to use Terraform for provisioning infrastructure.

1. **Initialize Terraform:**

   ```bash
   cd Terraform
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

## 🔍 Exploring the Application

Once the application is deployed, you can typically access it through a Service (Kubernetes) or by port-forwarding to a pod. Refer to the specific deployment method's documentation for details.

* **API Endpoints:** The Spring Boot application exposes various REST endpoints.
* **Actuator Endpoints:** Monitoring information is available via Spring Boot Actuator (e.g., `/actuator/health`, `/actuator/metrics`).
* **Swagger UI:** API documentation is available through Swagger UI (commonly `/swagger-ui.html`, depending on your configuration).

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
