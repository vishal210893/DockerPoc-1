# Helm Charts and Terraform Integration Guide

## 📚 Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Helm Charts](#helm-charts)
- [Terraform Configuration](#terraform-configuration)
- [Integration Guide](#integration-guide)
- [Deployment Process](#deployment-process)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

This guide explains how to use Helm charts and Terraform together to deploy the DockerPoc-1 application. The integration allows for:
- Infrastructure as Code (IaC) with Terraform
- Kubernetes application deployment with Helm
- Automated infrastructure and application deployment
- Version-controlled infrastructure and application configurations

## 📋 Prerequisites

- Terraform v1.0.0+
- Helm v3.0.0+
- kubectl configured
- AWS CLI configured (if using AWS)
- Docker installed
- Access to a Kubernetes cluster

## 🎨 Helm Charts

### Chart Structure
```
helm/
├── Chart.yaml              # Chart metadata
├── values.yaml            # Default configuration values
├── templates/             # Kubernetes manifest templates
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   └── secrets.yaml
└── charts/               # Dependencies
```

### Key Components

1. **Chart.yaml**
```yaml
apiVersion: v2
name: dockerpoc-1
description: A Helm chart for DockerPoc-1 application
version: 0.1.0
appVersion: "1.0.0"
```

2. **values.yaml**
```yaml
replicaCount: 1
image:
  repository: dockerpoc-1
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 8005

ingress:
  enabled: true
  annotations:
    kubernetes.io/ingress.class: nginx
  hosts:
    - host: dockerpoc-1.local
      paths:
        - path: /
          pathType: Prefix

resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 250m
    memory: 256Mi
```

### Installing the Chart

```bash
# Add the repository
helm repo add dockerpoc-1 https://your-helm-repo-url

# Install the chart
helm install dockerpoc-1 dockerpoc-1/dockerpoc-1 \
  --namespace your-namespace \
  --create-namespace \
  --values custom-values.yaml
```

## 🏗️ Terraform Configuration

### Project Structure
```
terraform/
├── main.tf              # Main Terraform configuration
├── variables.tf         # Input variables
├── outputs.tf          # Output values
├── providers.tf        # Provider configurations
└── modules/            # Reusable modules
    ├── kubernetes/     # Kubernetes module
    └── helm/          # Helm module
```

### Key Components

1. **providers.tf**
```hcl
terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}

provider "helm" {
  kubernetes {
    config_path = "~/.kube/config"
  }
}
```

2. **main.tf**
```hcl
module "kubernetes" {
  source = "./modules/kubernetes"
  
  cluster_name = var.cluster_name
  region      = var.region
}

module "helm" {
  source = "./modules/helm"
  
  namespace = var.namespace
  chart_version = var.chart_version
  
  depends_on = [module.kubernetes]
}
```

## 🔄 Integration Guide

### Terraform Creating Helm Charts

1. **Helm Release Resource**
```hcl
resource "helm_release" "dockerpoc" {
  name       = "dockerpoc-1"
  repository = "https://your-helm-repo-url"
  chart      = "dockerpoc-1"
  namespace  = var.namespace
  version    = var.chart_version

  set {
    name  = "replicaCount"
    value = var.replica_count
  }

  set {
    name  = "image.tag"
    value = var.image_tag
  }

  values = [
    file("${path.module}/values.yaml")
  ]
}
```

2. **Dynamic Values**
```hcl
locals {
  helm_values = {
    replicaCount = var.replica_count
    image = {
      repository = var.image_repository
      tag        = var.image_tag
    }
    ingress = {
      enabled = var.enable_ingress
      hosts   = var.ingress_hosts
    }
  }
}
```

## 🚀 Deployment Process

1. **Initialize Terraform**
```bash
terraform init
```

2. **Plan the Deployment**
```bash
terraform plan -out=tfplan
```

3. **Apply the Configuration**
```bash
terraform apply tfplan
```

4. **Verify Deployment**
```bash
kubectl get pods -n your-namespace
helm list -n your-namespace
```

## 🔧 Troubleshooting

### Common Issues and Solutions

1. **Helm Chart Not Found**
```bash
# Verify repository
helm repo list
helm repo update

# Check chart availability
helm search repo dockerpoc-1
```

2. **Terraform Provider Issues**
```bash
# Clean Terraform state
terraform init -reconfigure

# Verify provider versions
terraform providers
```

3. **Kubernetes Connection Issues**
```bash
# Verify kubectl configuration
kubectl config view
kubectl cluster-info
```

### Debugging Commands

```bash
# Check Terraform state
terraform state list
terraform state show helm_release.dockerpoc

# Check Helm release status
helm status dockerpoc-1 -n your-namespace

# Check Kubernetes resources
kubectl get all -n your-namespace
```

## 📝 Best Practices

1. **Version Control**
   - Use semantic versioning for Helm charts
   - Pin Terraform provider versions
   - Store sensitive values in Terraform variables

2. **Security**
   - Use Kubernetes secrets for sensitive data
   - Implement RBAC policies
   - Enable network policies

3. **Maintenance**
   - Regular updates of dependencies
   - Backup of Terraform state
   - Document all custom values

## 🔍 Monitoring and Logging

1. **Terraform State**
```bash
# View state
terraform show

# List resources
terraform state list
```

2. **Helm Release**
```bash
# Get release history
helm history dockerpoc-1 -n your-namespace

# Get release values
helm get values dockerpoc-1 -n your-namespace
```

## 📚 Additional Resources

- [Terraform Documentation](https://www.terraform.io/docs)
- [Helm Documentation](https://helm.sh/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs)
- [Terraform Helm Provider](https://registry.terraform.io/providers/hashicorp/helm/latest/docs) 