output "helm_release_name" {
  description = "Name of the Helm release."
  value       = helm_release.dockerpoc.name
}

output "helm_release_namespace" {
  description = "Namespace of the Helm release."
  value       = helm_release.dockerpoc.namespace
}

output "helm_release_status" {
  description = "Status of the Helm release."
  value       = helm_release.dockerpoc.status
}

output "ingress_service_name" {
  description = "The name of the service targeted by the Ingress (if Ingress is enabled)."
  value = try(
    yamldecode(helm_release.dockerpoc.metadata[0].values).ingress.paths[0].backend.serviceName,
    "N/A - Ingress path or serviceName not found, or Ingress not enabled/configured in values"
  )
  # Explanation:
  # 1. helm_release.dockerpoc_release.metadata[0].values gives the computed values as a YAML string.
  # 2. yamldecode(...) parses this string into a Terraform map/list structure.
  # 3. .ingress.paths[0].backend.serviceName navigates this structure.
  #    - .ingress accesses the 'ingress' map.
  #    - .paths accesses the 'paths' list within 'ingress'.
  #    - [0] accesses the first item in the 'paths' list.
  #    - .backend accesses the 'backend' map within that list item.
  #    - .serviceName accesses the 'serviceName' string.
  # 4. try(...) provides a fallback value if any part of this path doesn't exist,
  #    preventing errors if, for example, ingress is disabled or paths are structured differently.
}

# Optional: To see the full decoded values structure for debugging
# output "all_decoded_helm_values" {
#   description = "All computed and decoded Helm values for debugging."
#   value       = yamldecode(helm_release.dockerpoc_release.metadata[0].values)
#   sensitive   = true # Mark as sensitive as it will show all values
# }