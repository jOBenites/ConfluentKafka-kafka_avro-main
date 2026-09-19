# Order Service - Quarkus + RabbitMQ

Microservicio de procesamiento de órdenes migrado de Spring Boot + Kafka a **Quarkus 3.20 + RabbitMQ**.

## Stack

- **Runtime:** Quarkus 3.20.3, Java 21
- **Messaging:** RabbitMQ via SmallRye Reactive Messaging
- **API:** JAX-RS REST (puerto 8181)
- **Container:** Multi-stage Dockerfile, non-root user
- **Infraestructura:** AKS (Azure Kubernetes Service) con Terraform

---

## Desarrollo Local

### Prerrequisitos
- Java 21
- Docker Desktop

### 1. Levantar RabbitMQ

```bash
docker-compose up -d
```

RabbitMQ Management UI: http://localhost:15672 (guest/guest)

### 2. Ejecutar la aplicación

```bash
./mvnw quarkus:dev
```

### 3. Probar la API

```bash
# Health check
curl http://localhost:8181/q/health

# Enviar orden
curl -X POST http://localhost:8181/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1,
    "orderDescription": "Compra de laptop",
    "orderAddress": "Av. Principal 123"
  }'
```

### 4. Detener

```bash
docker-compose down --volumes
```

---

## Despliegue en Azure AKS

### Prerrequisitos
- [Azure CLI](https://docs.microsoft.com/cli/azure/install-azure-cli) (dentro de WSL en Windows)
- [Terraform](https://developer.hashicorp.com/terraform/install)
- [Docker](https://docs.docker.com/get-docker/)
- Cuenta Azure con suscripción activa

### 1. Login en Azure

```bash
az login --use-device-code
```

### 2. Crear infraestructura con Terraform

```bash
cd terraform
terraform init
terraform plan
terraform apply -auto-approve
```

Esto crea:
- Resource Group `order-service-rg` (East US)
- AKS cluster `order-service-aks` (Free tier, 1 nodo `Standard_D2s_v7`)

### 3. Conectar kubectl al cluster

```bash
az aks get-credentials \
  --resource-group order-service-rg \
  --name order-service-aks
```

Verificar:
```bash
kubectl get nodes
```

### 4. Construir y subir imagen Docker

```bash
# Login a Docker Hub
docker login

# Build
docker build -t <tu-usuario>/order-service:latest .

# Push
docker push <tu-usuario>/order-service:latest
```

### 5. Actualizar manifiestos K8s

Reemplaza `<tu-usuario>` en los archivos:
- `k8s/api-deployment.yaml` → image: `docker.io/<tu-usuario>/order-service:latest`
- `k8s/consumer-deployment.yaml` → image: `docker.io/<tu-usuario>/order-service:latest`

### 6. Desplegar en AKS

```bash
kubectl apply -f k8s/
```

### 7. Verificar

```bash
# Pods
kubectl get pods -n order-service

# IP externa del LoadBalancer
kubectl get service order-api-service -n order-service
```

### 8. Probar la API en AKS

```bash
# Health check
curl http://<EXTERNAL-IP>/q/health

# Enviar orden
curl -X POST http://<EXTERNAL-IP>/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": 1,
    "orderDescription": "Orden desde AKS",
    "orderAddress": "Calle 123, Ciudad"
  }'
```

También puedes usar la **Postman Collection** incluida en el proyecto (`BOOTCAMP NTTDATA -CONFLUENT.postman_collection.json`). Actualiza la variable `baseUrl` con la IP externa del LoadBalancer.

### 9. Destruir infraestructura

```bash
cd terraform
terraform destroy -auto-approve
```

---

## Estructura del Proyecto

```
├── src/main/java/.../
│   ├── controller/EventController.java    # REST API
│   ├── producer/OrderProducer.java        # SmallRye emitter → RabbitMQ
│   ├── consumer/OrderConsumer.java        # SmallRye @Incoming ← RabbitMQ
│   └── dto/
│       ├── OrderMessage.java              # DTO JSON
│       └── orderRecord.java               # Avro-generated
├── src/test/java/.../
│   └── controller/EventControllerTest.java
├── k8s/                                   # Manifiestos Kubernetes
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── rabbitmq-deployment.yaml
│   ├── api-deployment.yaml
│   ├── api-service.yaml
│   └── consumer-deployment.yaml
├── terraform/                             # Infraestructura AKS
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── terraform.tfvars
├── Dockerfile
├── docker-compose.yml                     # Solo RabbitMQ
└── pom.xml
```

## Endpoints

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/q/health` | Health check (SmallRye Health) |
| `POST` | `/api/events` | Enviar orden a RabbitMQ |

### POST /api/events

**Request body:**
```json
{
  "orderId": 1,
  "orderDescription": "Descripción de la orden",
  "orderAddress": "Dirección de entrega"
}
```

**Success response (200):**
```json
{
  "success": true,
  "timestamp": "2026-09-18 23:00:00",
  "message": "Orden #1 enviada exitosamente a RabbitMQ | MessageId: xxx",
  "data": {
    "orderId": 1,
    "orderDescription": "...",
    "orderAddress": "..."
  }
}
```

**Validation error (400):**
```json
{
  "success": false,
  "timestamp": "...",
  "error": "Validacion fallida",
  "validationErrors": ["orderId debe ser un numero positivo mayor a 0"]
}
```
