#!/bin/bash
set -e

echo "=========================================="
echo "Deploy Order Service to AKS"
echo "=========================================="

# 1. Login to Azure
echo "Step 1: Login to Azure..."
az login

# 2. Initialize Terraform
echo "Step 2: Initialize Terraform..."
cd terraform
terraform init

# 3. Plan Terraform
echo "Step 3: Plan Terraform..."
terraform plan -out=tfplan

# 4. Apply Terraform
echo "Step 4: Apply Terraform..."
terraform apply tfplan

# 5. Get kubeconfig
echo "Step 5: Get kubeconfig..."
az aks get-credentials \
  --resource-group $(terraform output -raw resource_group_name) \
  --name $(terraform output -raw aks_name) \
  --overwrite-existing

# 6. Build Docker image (using Docker Hub)
echo "Step 6: Build Docker image..."
cd ..
docker build -t your-dockerhub-user/order-service:latest .

# 7. Push to Docker Hub
echo "Step 7: Push to Docker Hub..."
docker push your-dockerhub-user/order-service:latest

# 8. Update k8s manifests with Docker Hub image
echo "Step 8: Update k8s manifests..."
sed -i "s|<ACR_NAME>.azurecr.io|docker.io/your-dockerhub-user|g" k8s/api-deployment.yaml k8s/consumer-deployment.yaml

# 9. Deploy to Kubernetes
echo "Step 9: Deploy to Kubernetes..."
kubectl apply -f k8s/

# 10. Wait for rollout
echo "Step 10: Wait for rollout..."
kubectl rollout status deployment/order-api -n order-service
kubectl rollout status deployment/order-consumer -n order-service

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Check pods:"
kubectl get pods -n order-service
echo ""
echo "Get external IP:"
kubectl get service order-api-service -n order-service
echo ""
echo "Test API:"
echo "curl http://<EXTERNAL-IP>/api/events"
