# Plan de migracion: Spring Boot a Quarkus, RabbitMQ y AKS

## Objetivo

Migrar el proyecto en etapas independientes para reducir el riesgo:

1. Migrar Spring Boot a Quarkus manteniendo Kafka y Schema Registry.
2. Sustituir Kafka por RabbitMQ.
3. Contenerizar y desplegar la aplicacion en Azure Kubernetes Service (AKS).

La primera etapa debe quedar funcional antes de comenzar la siguiente.

## Estado actual

El proyecto expone `POST /api/events`, valida un `orderRecord`, lo publica en Kafka y lo consume mediante listeners Kafka. Actualmente utiliza:

- Spring Boot 3.3.0.
- Java 21.
- Spring Web.
- Spring Kafka.
- Kafka Avro Serializer.
- Confluent Schema Registry.
- Kafka Streams y Kafka Clients.
- Docker Compose con Zookeeper, Kafka y Schema Registry.

El contrato Avro se encuentra en `src/main/resources/avro/order.avsc`.

## Principios de la migracion

- Una etapa funcional por vez.
- No cambiar framework y broker en el mismo cambio.
- Mantener inicialmente el contrato HTTP y el contrato Avro.
- Mantener Kafka operativo durante la migracion Spring Boot -> Quarkus.
- No retirar dependencias ni infraestructura hasta que existan pruebas equivalentes.
- Cada etapa debe poder desplegarse y revertirse de forma independiente.

---

## Fase 0: linea base de Spring Boot

### Objetivo

Registrar el comportamiento actual antes de cambiar el framework.

### Actividades

- Ejecutar `mvnw clean test`.
- Levantar el entorno actual con Docker Compose.
- Crear el topic `order-topic` con sus tres particiones.
- Verificar Schema Registry y el esquema Avro.
- Probar `POST /api/events` con:
  - payload valido;
  - payload incompleto;
  - datos invalidos;
  - Kafka detenido.
- Registrar los codigos HTTP y la forma de las respuestas.
- Registrar que el mensaje publicado sea consumido correctamente.
- Guardar ejemplos de request, response y logs relevantes.

### Criterio de salida

Existe una prueba reproducible del flujo actual y se conocen sus respuestas esperadas.

### Ejecucion inicial

- `./mvnw.cmd clean test`: correcto; 1 prueba ejecutada, 0 fallos y 0 errores.
- La prueba actual valida el arranque del contexto Spring, pero no valida el flujo completo de publicacion y consumo.
- La prueba mostro intentos de conexion a `localhost:9092` sin broker disponible.
- Docker Desktop ya responde correctamente.
- Las tres imagenes de Confluent quedaron descargadas correctamente.
- Se resolvio el conflicto con el contenedor externo `kafka-dev` usando Kafka en `localhost:19092` y Schema Registry en `localhost:18081`.
- El entorno queda operativo con Zookeeper, Kafka y Schema Registry.
- `order-topic` fue creado con 3 particiones.
- Schema Registry requirio `JAVA_TOOL_OPTIONS=-XX:-UseContainerSupport` por un fallo cgroupv2/JMX de la imagen en Docker Desktop.
- `./mvnw.cmd test`: correcto; 1 prueba ejecutada, 0 fallos y 0 errores.
- Los consumidores Spring se conectaron al broker y recibieron asignacion de las particiones 0, 1 y 2.

---

## Fase 1: migracion de Spring Boot a Quarkus manteniendo Kafka

Esta es la primera implementacion. En esta fase no se cambia RabbitMQ ni AKS.

### Alcance

Se conserva sin cambios funcionales:

- Kafka como broker.
- Topic `order-topic`.
- Tres particiones actuales.
- Confluent Schema Registry.
- Serializacion y deserializacion Avro.
- Endpoint `POST /api/events`.
- Estructura de `orderRecord`.

Se cambia solamente:

- Spring Boot por Quarkus.
- Anotaciones Spring por APIs Quarkus/Jakarta.
- Configuracion Spring por configuracion Quarkus.
- Pruebas Spring por pruebas Quarkus.

### 1. Preparar el nuevo `pom.xml`

Eliminar gradualmente:

- `spring-boot-starter-parent`.
- `spring-boot-starter-web`.
- `spring-kafka`.
- `spring-boot-starter-test`.
- `spring-kafka-test`.
- `spring-boot-maven-plugin`.

Agregar extensiones Quarkus equivalentes:

- `quarkus-rest-jackson`.
- `quarkus-arc`.
- `quarkus-smallrye-health`.
- Extensiones Kafka de Quarkus o SmallRye Reactive Messaging, segun la estrategia elegida.

Conservar inicialmente:

- Avro.
- Kafka clients, si la implementacion seleccionada los requiere.
- Confluent Kafka Avro Serializer.
- Cliente de Schema Registry.
- Plugin Maven de Avro.

La migracion debe mantener Java 21.

### 2. Migrar la clase de arranque

Reemplazar `KafkaSchemaRegistryApplication` basado en `@SpringBootApplication` por el arranque estandar de Quarkus. El nombre de la clase puede conservarse temporalmente para reducir cambios, aunque se recomienda renombrarlo posteriormente a un nombre neutro como `OrderMessagingApplication`.

### 3. Migrar el controlador REST

Convertir `EventController`:

- `@RestController` -> `@Path`.
- `@RequestMapping` -> `@Path`.
- `@PostMapping` -> `@POST`.
- `@RequestBody` -> parametro con `@Consumes`.
- `ResponseEntity` -> respuestas HTTP de Quarkus/JAX-RS.
- Inyeccion Spring -> `@Inject`.

Conservar:

- Ruta `/api/events`.
- Validaciones actuales.
- Respuestas de exito y error.
- Codigos HTTP.
- Serializacion JSON del request y response.

No mezclar en esta fase cambios de contrato API ni redisenos del DTO.

### 4. Migrar el productor Kafka

Convertir `KafkaAvroProducer21` a un bean CDI de Quarkus.

Mantener temporalmente:

- `order-topic`.
- La clave UUID del mensaje.
- Serializacion Avro.
- Schema Registry.
- Particion por defecto, si es necesaria para compatibilidad.
- Confirmacion de publicacion antes de responder al cliente.

Eliminar el uso directo de anotaciones Spring y revisar el manejo de `CompletableFuture` para adaptarlo al modelo reactivo o asincrono de Quarkus. No introducir todavia RabbitMQ.

### 5. Migrar el consumidor Kafka

Convertir `KafkaAvroConsumer` a la API Kafka elegida para Quarkus.

Mantener:

- Topic `order-topic`.
- Consumo de las tres particiones.
- Deserializacion Avro.
- Grupo de consumidores.
- Comportamiento de confirmacion y reintentos actual, documentando cualquier diferencia.

Verificar especialmente que el reemplazo de `@KafkaListener`, `ConsumerRecord` y la asignacion de particiones no cambie el comportamiento.

### 6. Migrar la configuracion

Trasladar `application.yml` a la configuracion de Quarkus, manteniendo inicialmente los mismos valores funcionales:

- Servidor HTTP en el puerto `8181`.
- Broker Kafka configurable por variable de entorno.
- URL de Schema Registry configurable por variable de entorno.
- Nombre del topic.
- Grupo de consumidores.
- Propiedades Avro.
- Particion por defecto.

Las credenciales y endpoints no deben quedar codificados en el repositorio.

### 7. Migrar y ampliar pruebas

Crear pruebas para:

- Levantamiento del contexto Quarkus.
- `POST /api/events` con payload valido.
- Validaciones de campos.
- Publicacion Kafka.
- Consumo Kafka.
- Deserializacion Avro.
- Error de conexion con Kafka.
- Error de Schema Registry.

Usar Testcontainers Kafka y Schema Registry, o el mecanismo de integracion que soporte el entorno del proyecto. La prueba de integracion debe verificar el flujo completo y no solamente que el contexto arranque.

### Criterios de salida de la Fase 1

- La aplicacion Quarkus compila y arranca con Java 21.
- `POST /api/events` conserva el contrato actual.
- Kafka sigue siendo el unico broker utilizado.
- Los mensajes Avro siguen siendo compatibles con el consumidor actual.
- Las tres particiones siguen funcionando.
- Las pruebas de regresion pasan.
- La imagen Docker de Quarkus puede ejecutar el flujo local.
- Se puede volver a la version Spring Boot sin cambios en Kafka ni en los esquemas.

### Resultado de implementacion

- Quarkus `3.20.3` compila y arranca con Java 21.
- Se migraron el arranque, el controlador REST, el productor Kafka, el consumidor Kafka y la prueba de contexto.
- Kafka, Avro y Schema Registry se mantienen; no se introdujo RabbitMQ.
- El fast-jar `target/quarkus-app/quarkus-run.jar` se genera correctamente.
- `POST /api/events` valido publico la orden `1002` en `order-topic`, particion 1, offset 2.
- Un payload invalido devolvio HTTP 400.
- `./mvnw.cmd test`: correcto; 1 prueba ejecutada, 0 fallos y 0 errores.
- La imagen Docker fue adaptada al formato fast-jar de Quarkus.
- La validacion de flujo se realizo con Kafka en `localhost:19092` y Schema Registry en `localhost:18081`.
- Se agregaron 3 pruebas REST para el controlador: orden valida, validacion invalida y fallo de publicacion.
- Suite completa: `./mvnw.cmd test` correcto; 4 pruebas ejecutadas, 0 fallos y 0 errores.

---

## Fase 2: sustitucion de Kafka por RabbitMQ

Esta fase comienza solamente cuando la Fase 1 este validada.

### Decisiones que deben cerrarse antes de implementar

- Usar JSON o mantener Avro binario.
- Si se mantiene Avro, definir el repositorio de esquemas. Confluent Schema Registry no debe conservarse automaticamente porque su persistencia esta ligada a Kafka.
- Definir exchanges, queues, routing keys y dead-letter queues.
- Definir politica de reintentos y mensajes duplicados.
- Definir si las tres particiones Kafka se reemplazan por consumidores paralelos o por colas funcionales separadas.

### Implementacion prevista

- Reemplazar el canal Kafka por SmallRye Reactive Messaging RabbitMQ.
- Crear un exchange `orders.exchange`.
- Crear una cola de procesamiento `orders.processing`.
- Crear una cola de reintentos y una dead-letter queue.
- Implementar acknowledgements solamente despues del procesamiento exitoso.
- Agregar idempotencia usando `message-id` o `orderId`.
- Actualizar Docker Compose para RabbitMQ.
- Crear pruebas de integracion con Testcontainers RabbitMQ.

### Criterios de salida

- No quedan dependencias Kafka en el runtime.
- Los mensajes se publican, consumen, reintentan y envian a DLQ correctamente.
- La API mantiene el contrato externo salvo cambios documentados.
- La observabilidad permite distinguir errores de publicacion, consumo y serializacion.

---

## Fase 3: contenedorizacion y despliegue en AKS

Esta fase comienza cuando Quarkus y RabbitMQ ya funcionen localmente y las pruebas de integracion pasen.

### Infraestructura

- Azure Container Registry para almacenar la imagen.
- AKS para ejecutar API y consumidor.
- RabbitMQ administrado, preferentemente fuera del cluster de aplicacion.
- Azure Key Vault para secretos.
- Workload Identity para acceso seguro desde AKS.
- Ingress o Application Gateway para el endpoint HTTP.

### Kubernetes

Crear manifests o Helm chart para:

- Deployment del API.
- Deployment del consumidor.
- Services internos.
- Ingress.
- ConfigMap.
- Secret o integracion CSI con Key Vault.
- Probes de startup, readiness y liveness.
- HorizontalPodAutoscaler.
- PodDisruptionBudget.
- NetworkPolicy.

El API y el consumidor deben escalarse por separado. El consumidor puede escalarse segun la profundidad de la cola usando KEDA.

### Criterios de salida

- La imagen se construye y publica automaticamente.
- El despliegue es reproducible desde CI/CD.
- Las credenciales no estan en la imagen ni en Git.
- AKS reporta correctamente la salud de los pods.
- Se observan logs, metricas y trazas.
- Se prueba reinicio de pods, perdida temporal de RabbitMQ y crecimiento de la cola.

---

## Orden recomendado de entregas

1. Baseline de Spring Boot y Kafka.
2. Proyecto Quarkus ejecutando el endpoint.
3. Productor Kafka en Quarkus.
4. Consumidor Kafka en Quarkus.
5. Pruebas de regresion completas.
6. RabbitMQ local y nuevo contrato de mensajeria.
7. Pruebas de reintentos, DLQ e idempotencia.
8. Imagen Docker de Quarkus.
9. Despliegue en AKS no productivo.
10. Pruebas de carga y resiliencia.
11. Produccion y retiro de Kafka.

## Riesgos principales

- Intentar migrar framework y broker simultaneamente dificulta identificar fallos.
- Confluent Schema Registry puede no ser viable despues de retirar Kafka.
- RabbitMQ no tiene particiones Kafka; la semantica debe redisenarse.
- Un consumidor RabbitMQ puede recibir mensajes duplicados y debe ser idempotente.
- Ejecutar RabbitMQ en AKS requiere operar persistencia, alta disponibilidad y backups.
- Las respuestas HTTP actuales dependen de la confirmacion asincrona del productor y deben conservar esa garantia.

## Resultado esperado

Al finalizar, el proyecto habra pasado por tres cambios controlados: primero Quarkus conservando Kafka, despues RabbitMQ conservando la funcionalidad de negocio y finalmente AKS como plataforma de ejecucion. Cada etapa tendra pruebas y criterios de salida propios.
