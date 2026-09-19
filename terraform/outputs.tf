output "resource_group_name" {
  description = "Nombre del Resource Group"
  value       = azurerm_resource_group.main.name
}

output "aks_name" {
  description = "Nombre del cluster AKS"
  value       = azurerm_kubernetes_cluster.main.name
}

output "aks_id" {
  description = "ID del cluster AKS"
  value       = azurerm_kubernetes_cluster.main.id
}

output "kube_config" {
  description = "Configuración de kubectl"
  value       = azurerm_kubernetes_cluster.main.kube_config_raw
  sensitive   = true
}

output "host" {
  description = "Host del API server de AKS"
  value       = azurerm_kubernetes_cluster.main.kube_config[0].host
  sensitive   = true
}

output "node_resource_group" {
  description = "Resource Group auto-generado por AKS"
  value       = azurerm_kubernetes_cluster.main.node_resource_group
}
