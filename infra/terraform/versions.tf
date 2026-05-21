terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
  }
}

# Configure the Kubernetes provider
provider "kubernetes" {
  # Explicitly set the kubeconfig path.
  # Replace with the actual path to your kubeconfig file if it's not the default.
  # The tilde (~) for home directory expansion works here.
  config_path = "~/.kube/config"

  # If you have multiple contexts in your kubeconfig and want to specify one:
  # config_context = "k3d-vela" # Replace with your k3d cluster's context name
}

# Configure the Helm provider
provider "helm" {
  kubernetes {
    # Explicitly set the kubeconfig path for Helm's Kubernetes client.
    config_path = "~/.kube/config"

    # If you have multiple contexts in your kubeconfig and want to specify one:
    # config_context = "k3d-vela" # Replace with your k3d cluster's context name
  }
}