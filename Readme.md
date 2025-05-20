# DockerPoc-1

A Spring Boot application demonstrating Docker, Kubernetes, and CI/CD best practices with GitHub Actions.

## 🚀 Overview

This project is a Spring Boot application that showcases:
- Docker containerization
- Kubernetes deployment
- CI/CD with GitHub Actions
- Remote debugging capabilities
- Database integration (PostgreSQL)
- API documentation with OpenAPI/Swagger
- Monitoring with Spring Boot Actuator

## 🛠️ Tech Stack

- Java 17
- Spring Boot 3.4.5
- PostgreSQL
- Docker
- Kubernetes
- GitHub Actions
- Maven
- Lombok
- Spring Data JPA
- SpringDoc OpenAPI
- Spring Boot Actuator

## 📋 Prerequisites

- JDK 17
- Maven
- Docker
- Kubernetes cluster (for deployment)
- kubectl (for Kubernetes operations)
- PostgreSQL (for production)

## 🏗️ Project Structure

```
.
├── src/                    # Source code
├── K8s_Yaml/              # Kubernetes manifests
│   ├── App/               # Application deployments
│   ├── Config/            # ConfigMaps and Secrets
│   ├── Ingress/           # Ingress configurations
│   ├── Network/           # Network policies
│   ├── Probe/             # Health checks
│   ├── RBAC/              # Role-based access control
│   ├── Schedule/          # Scheduled jobs
│   ├── Storage/           # Persistent storage
│   └── kubevela/          # KubeVela configurations
├── nginx/                 # Nginx configurations
├── .github/workflows/     # GitHub Actions workflows
├── Dockerfile             # Docker build file
├── docker-compose.yml     # Docker Compose configuration
└── pom.xml               # Maven configuration
```

## 🚀 Getting Started

### Local Development

1. Clone the repository:
   ```bash
   git clone https://github.com/vishal210893/DockerPoc-1.git
   cd DockerPoc-1
   ```

2. Build the application:
   ```bash
   mvn clean package
   ```

3. Run with Docker Compose:
   ```bash
   docker-compose up
   ```

### Remote Debugging

The application supports remote debugging on port 5005. To enable debugging:

1. Start the application with debug mode:
   ```bash
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar target/dockerpoc-1.jar
   ```

2. Configure your IDE to connect to localhost:5005

## 🐳 Docker

### Building the Image

```bash
docker build -t dockerpoc-1 .
```

### Running the Container

```bash
docker run -p 8005:8005 -p 5005:5005 dockerpoc-1
```

## ☸️ Kubernetes Deployment

### Prerequisites

- Kubernetes cluster
- kubectl configured
- Docker image pushed to registry

### Deployment Steps

1. Apply Kubernetes configurations:
   ```bash
   kubectl apply -f K8s_Yaml/
   ```

2. Verify deployment:
   ```bash
   kubectl get pods
   kubectl get services
   ```

## 🔄 CI/CD Pipeline

The project uses GitHub Actions for CI/CD. The workflow includes:

1. Build and test
2. Docker image creation
3. Push to container registry
4. Kubernetes deployment

## 📊 Monitoring

The application includes Spring Boot Actuator endpoints for monitoring:

- Health check: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

## 📚 API Documentation

OpenAPI/Swagger documentation is available at:
```
http://localhost:8005/swagger-ui.html
```

## 🔐 Environment Variables

Key environment variables:
- `SPRING_PROFILES_ACTIVE`: Application profile (default: prod)
- `DB_PASSWORD`: Database password
- `DEBUG_PORT`: Remote debugging port (default: 5005)

## 🛡️ Security

- RBAC configurations in `K8s_Yaml/RBAC/`
- Network policies in `K8s_Yaml/Network/`
- Secure secret management

## 📝 Logging

Logs are stored in the `logs/` directory and can be accessed through:
- Container logs
- Kubernetes pod logs
- Spring Boot Actuator endpoints

## 🔧 Troubleshooting

1. Check container logs:
   ```bash
   docker logs <container-id>
   ```

2. Check Kubernetes pod logs:
   ```bash
   kubectl logs <pod-name>
   ```

3. Verify Kubernetes resources:
   ```bash
   kubectl get all
   ```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Authors

- Vishal Kumar - [vishal210893](https://github.com/vishal210893)

## 🙏 Acknowledgments

- Spring Boot team
- Docker community
- Kubernetes community