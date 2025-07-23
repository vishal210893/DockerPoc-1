variable "k8s_namespace" {
  description = "The Kubernetes namespace to deploy the Helm chart into."
  type        = string
  default     = "default"
}

variable "release_name" {
  description = "The name for the Helm release."
  type        = string
  default     = "dockerpoc" # Using a different name to distinguish from Makefile deployments
}

variable "chart_path" {
  description = "The relative path to the Helm chart directory from where terraform is run."
  type        = string
  default     = "../Helm" # Assumes terraform files are in a subdirectory like 'terraform-k3d-deploy'
}

variable "create_namespace" {
  description = "Whether to create the Kubernetes namespace if it doesn't exist."
  type        = bool
  default     = true
}

variable "k3d_default_storage_class" {
  description = "The default storage class provided by your k3d cluster (usually 'local-path')."
  type        = string
  default     = "local-path"
}