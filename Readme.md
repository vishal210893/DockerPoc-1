# Remote Debugging with IntelliJ IDEA

This guide explains how to configure remote debugging for a Java application running inside a Kubernetes pod using IntelliJ IDEA.

## Prerequisites
- IntelliJ IDEA installed
- `kubectl` configured for your Kubernetes cluster
- Access to modify the application's Dockerfile and Kubernetes manifests

---

## Phase 1: Modify Application & Kubernetes Configuration

### Step 1: Enable JVM Debug Agent in Dockerfile
Modify your `Dockerfile` to enable the Java Debug Wire Protocol (JDWP) agent using the `JAVA_TOOL_OPTIONS` environment variable.

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jdk

# Set debug port (can be parameterized)
ENV DEBUG_PORT=5005

# Enable JDWP agent
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}"

COPY /path/to/your/application.jar /app/
CMD ["java", "-jar", "/app/application.jar"]
```

**Important Notes:**
- Replace `/path/to/your/application.jar` with your actual JAR path
- Use `address=*:5005` to bind to all interfaces
- `suspend=n` allows the app to start without waiting for debugger attachment

### Step 2: Expose Debug Port in Kubernetes Deployment
Update your Kubernetes Deployment manifest to expose the debug port.

**Deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: your-app-deployment
spec:
  template:
    spec:
      containers:
        - name: main-app
          ports:
            - containerPort: 8080  # Your application port
            - containerPort: 5005  # Debug port
          env:
            - name: DEBUG_PORT
              value: "5005"
```

---

## Phase 2: Build, Deploy, and Port-Forward

### Step 3: Rebuild and Redeploy
Rebuild your Docker image and redeploy to Kubernetes:

```bash
./deploy.sh --build
```

**Note:** Stop the script with `Ctrl+C` before it reaches ingress port-forwarding (Step 6 in the script).

### Step 4: Identify Application Pod
Find your pod name after deployment:

```bash
kubectl get pods -l app=your-app-label
```

Example output:
```
NAME                                  READY   STATUS    
dockerpoc-1-deployment-abcd1234-xyz   1/1     Running
```

### Step 5: Start Port Forwarding
In a new terminal, forward the debug port:

```bash
kubectl port-forward <your-pod-name> 5005:5005
```

Example:
```bash
kubectl port-forward dockerpoc-1-deployment-abcd1234-xyz 5005:5005
```

**Keep this terminal running during debugging.**

---

## Phase 3: Configure IntelliJ Debugger

### Step 6: Create Remote Debug Configuration
1. Open project in IntelliJ
2. **Run** → **Edit Configurations...** → **+** → **Remote JVM Debug**
3. Configure settings:
   - **Name**: `Debug K8s App`
   - **Host**: `localhost`
   - **Port**: `5005`
   - **Command line arguments**: Should match your `JAVA_TOOL_OPTIONS`
   - **Module classpath**: Select your application's main module

### Step 7: Start Debugging
1. Set breakpoints in your code
2. Select `Debug K8s App` configuration
3. Click **Debug** (bug icon)
4. Verify connection in Debug Console
5. Trigger application functionality with breakpoints

---

## Important Considerations

### Security
- 🔒 Never enable debugging in production
- 🚨 Remove debug configurations after use
- 🔄 Rebuild and redeploy without debug settings

### Performance
- ⚖️ Use `replicas: 1` for easier debugging
- 💻 Allocate sufficient pod resources
- 🛑 Prefer `suspend=n` unless debugging startup code

### Network
- 🔥 Configure local firewall to allow debug port
- 🌐 Ensure corporate network permits port forwarding
- 🔗 Verify port-forwarding remains active

### Cleanup
- ⏹️ Stop IntelliJ debug session when finished
- 🚫 Terminate port-forwarding with `Ctrl+C`
- 🧹 Remove debug ports from Kubernetes manifests
``` 

This formatted version:
1. Uses consistent heading hierarchy
2. Groups related information with clear section breaks
3. Formats all code blocks with proper syntax highlighting
4. Organizes important notes in emphasized sections
5. Uses emojis for visual scanning in important considerations
6. Maintains logical flow between configuration steps
7. Includes placeholder for configuration screenshot
8. Provides clear cleanup instructions
9. Uses modern markdown formatting for better readability

Note: Replace the screenshot URL with an actual image reference when available.
```
---

## 🚀 CI/CD Workflow: Build, Dockerize & Deploy

This repository uses a multi-stage GitHub Actions workflow to automate building, containerizing, and updating your Kubernetes deployment.  
Below is a step-by-step breakdown of the process:

### 1️⃣ Build Java Artifact (`build-artifact` job)
- **1.1 Checkout code:** Retrieves the latest source code from the repository.
- **1.2 Set up Java:** Configures Java 17 environment using Temurin distribution.
- **1.3 Cache Maven repository:** Speeds up builds by caching Maven dependencies.
- **1.4 Build with Maven:** Compiles and packages the application JAR.
- **1.5 Upload JAR:** Stores the built JAR as a workflow artifact for later jobs.

### 2️⃣ Build & Push Docker Image (`docker-build-push` job)
- **2.1 Checkout code:** Ensures Docker context is available.
- **2.2 Download JAR:** Retrieves the JAR artifact from the previous job.
- **2.3 Generate image tag:** Creates a unique image tag based on timestamp.
- **2.4 Login to Docker Hub:** Authenticates to Docker Hub for image push.
- **2.5 Login to GHCR:** Authenticates to GitHub Container Registry.
- **2.6 Set up QEMU:** Enables multi-architecture builds.
- **2.7 Set up Docker Buildx:** Prepares advanced Docker build features.
- **2.8 Build & push image:** Builds and pushes the Docker image to both Docker Hub and GHCR.
- **2.9 (Optional) Save image tar:** Saves the Docker image as a tarball if enabled.
- **2.10 (Optional) Upload image artifact:** Uploads the tarball as a workflow artifact.

### 3️⃣ Update Kubernetes Deployment (`update-deployment` job)
- **3.1 Debug job outputs:** Prints image tag for traceability.
- **3.2 Checkout code:** Prepares the repository for patching.
- **3.3 Set Docker image:** Assembles the full image name with the generated tag.
- **3.4 Patch Deployment YAML:** Updates the Kubernetes manifest with the new image.
- **3.5 Commit and push:** Commits and pushes the updated manifest to the repository.

---

**Summary:**  
This workflow ensures your application is built, containerized, and deployed with a consistent, traceable image tag—fully automated from code push to Kubernetes update.