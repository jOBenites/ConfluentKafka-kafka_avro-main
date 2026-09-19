terraform {
  required_version = ">= 1.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {}
}

resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location

  tags = {
    Environment = var.environment
    Project     = "order-service"
  }
}

resource "azurerm_kubernetes_cluster" "main" {
  name                = var.aks_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = var.aks_dns_prefix
  kubernetes_version  = "1.35"

  default_node_pool {
    name           = "default"
    node_count     = var.node_count
    vm_size        = var.node_vm_size
    os_disk_size_gb = 30
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "kubenet"
    network_policy    = "calico"
    load_balancer_sku = "standard"
  }

  tags = {
    Environment = var.environment
    Project     = "order-service"
  }
}
