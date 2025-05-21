# Create the namespace if specified
resource "kubernetes_namespace" "app_namespace" {
  count = var.create_namespace && var.k8s_namespace != "default" ? 1 : 0 # Avoid trying to create 'default'
  metadata {
    name = var.k8s_namespace
  }
}

# Deploy the Helm chart
resource "helm_release" "dockerpoc" {
  name       = var.release_name
  chart      = var.chart_path
  namespace  = var.k8s_namespace
  depends_on = [kubernetes_namespace.app_namespace]

  # Use the values.yaml file from the chart directory
  values = [
    file("${var.chart_path}/values.yaml")
  ]

  # Optional: If you face issues with updates or dependencies
  # force_update      = true
  # recreate_pods     = true
  # cleanup_on_fail    = true
  # atomic             = true
}