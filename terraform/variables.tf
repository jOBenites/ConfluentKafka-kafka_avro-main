variable "resource_group_name" {
  description = "Nombre del Resource Group"
  type        = string
  default     = "order-service-rg"
}

variable "location" {
  description = "Región de Azure"
  type        = string
  default     = "eastus"
}

variable "aks_name" {
  description = "Nombre del cluster AKS"
  type        = string
  default     = "order-service-aks"
}

variable "aks_dns_prefix" {
  description = "DNS prefix para AKS"
  type        = string
  default     = "order-service"
}

variable "node_count" {
  description = "Número de nodos"
  type        = number
  default     = 1
}

variable "node_vm_size" {
  description = "Tamaño del nodo (VM)"
  type        = string
  default     = "Standard_D2s_v7"
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)"
  type        = string
  default     = "dev"
}
