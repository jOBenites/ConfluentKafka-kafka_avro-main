# Guia general de migracion de Spring Boot a Quarkus

## Objetivo

Migrar un proyecto Spring Boot a Quarkus conservando su comportamiento funcional y reduciendo el riesgo mediante cambios incrementales.

La recomendacion es separar la migracion en etapas:

1. Migrar Spring Boot a Quarkus.
2. Validar que el comportamiento sea equivalente.
3. Cambiar dependencias externas, por ejemplo Kafka por RabbitMQ.
4. Contenerizar y desplegar.

No conviene cambiar framework y broker en la misma etapa.

## 1. Crear una linea base

Antes de modificar el proyecto:

- Registrar la version de Java.
- Revisar las dependencias.
- Ejecutar las pruebas actuales.
- Probar los endpoints principales.
- Documentar codigos HTTP y respuestas.
- Validar integraciones externas.
- Construir la imagen actual si existe un Dockerfile.

Comando inicial:

```bash
./mvnw clean test
```

La linea base permite comparar Spring Boot y Quarkus con el mismo comportamiento esperado.

## 2. Cambiar el `pom.xml`

Eliminar progresivamente las dependencias propias de Spring Boot:

- `spring-boot-starter-parent`.
- `spring-boot-starter-web`.
- `spring-boot-starter-test`.
- `spring-kafka`, si se migra Kafka posteriormente.
- `spring-boot-maven-plugin`.

Agregar las dependencias Quarkus necesarias:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

Configurar el BOM y el plugin Maven de Quarkus. Mantener temporalmente las dependencias de negocio, como Avro, clientes Kafka, Schema Registry o clientes de bases de datos.

## 3. Migrar la clase principal

Spring Boot normalmente usa:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Quarkus no necesita una clase `main` para arrancar en producción. El plugin de Quarkus genera el arranque automáticamente.

La clase principal se puede eliminar o conservar temporalmente como clase vacía si existen referencias externas.

## 4. Migrar la inyeccion de dependencias

Equivalencias principales:

| Spring Boot | Quarkus/CDI |
|---|---|
| `@Component` | `@ApplicationScoped` |
| `@Service` | `@ApplicationScoped` |
| `@Repository` | `@ApplicationScoped` o extensión específica |
| `@Autowired` | `@Inject` |
| `@Value` | `@ConfigProperty` |
| `@PostConstruct` | `@PostConstruct` |
| `@PreDestroy` | `@PreDestroy` |

Ejemplo Spring:

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository repository;
}
```

Ejemplo Quarkus:

```java
@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository repository;
}
```

Cuando sea posible, preferir inyección por constructor para que las dependencias sean explícitas y fáciles de probar.

## 5. Migrar controladores REST

Ejemplo Spring:

```java
@RestController
@RequestMapping("/api")
public class EventController {

    @PostMapping("/events")
    public ResponseEntity<Order> create(@RequestBody Order order) {
        return ResponseEntity.ok(order);
    }
}
```

Ejemplo Quarkus:

```java
@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventController {

    @POST
    @Path("/events")
    public Response create(Order order) {
        return Response.ok(order).build();
    }
}
```

Equivalencias:

| Spring MVC | Jakarta REST |
|---|---|
| `@RestController` | `@Path` |
| `@RequestMapping` | `@Path` |
| `@GetMapping` | `@GET` |
| `@PostMapping` | `@POST` |
| `@PutMapping` | `@PUT` |
| `@DeleteMapping` | `@DELETE` |
| `@RequestBody` | Parámetro del método |
| `ResponseEntity` | `Response` |
| `@ExceptionHandler` | `ExceptionMapper` |

Verificar que no cambien accidentalmente:

- Las rutas.
- Los códigos HTTP.
- El formato JSON.
- Los mensajes de error.
- Los headers.
- Los contratos usados por otros clientes.

## 6. Migrar la configuracion

Spring Boot suele usar `application.yml`:

```yaml
server:
  port: 8181

spring:
  kafka:
    bootstrap-servers: localhost:9092
```

Quarkus puede usar `application.properties`:

```properties
quarkus.http.port=8181

kafka.bootstrap.servers=${KAFKA_BROKER:localhost:9092}
```

Para leer valores en Java:

```java
@ConfigProperty(name = "kafka.bootstrap.servers")
String bootstrapServers;
```

Usar variables de entorno para URLs, puertos, credenciales, topics y grupos de consumidores. No guardar secretos en el repositorio.

## 7. Migrar mensajeria

Si Kafka se conserva temporalmente, existen dos opciones.

### Cliente Kafka nativo

Permite una migracion directa y control manual de productores, consumidores, particiones y offsets:

```java
KafkaProducer<String, Order> producer;
KafkaConsumer<String, Order> consumer;
```

### SmallRye Reactive Messaging

Es la opcion mas integrada con Quarkus:

```java
@Incoming("orders")
public void consume(Order order) {
}
```

```java
@Channel("orders-out")
Emitter<Order> emitter;
```

Elegir una sola estrategia inicialmente. No cambiar Kafka por RabbitMQ durante la misma etapa de migracion del framework.

## 8. Migrar ciclo de vida y recursos

Para inicializacion y cierre:

```java
@PostConstruct
void start() {
}

@PreDestroy
void stop() {
}
```

También se pueden observar eventos de Quarkus:

```java
void onStart(@Observes StartupEvent event) {
}

void onStop(@Observes ShutdownEvent event) {
}
```

Cerrar correctamente productores, consumidores, pools de threads, clientes HTTP y conexiones de base de datos.

## 9. Migrar las pruebas

Spring Boot:

```java
@SpringBootTest
class ApplicationTests {
}
```

Quarkus:

```java
@QuarkusTest
class ApplicationTests {
}
```

No limitarse a probar que el contexto arranca. Agregar pruebas para:

- Controladores REST.
- Payloads válidos.
- Payloads inválidos.
- Validaciones.
- Códigos HTTP.
- Respuestas de error.
- Servicios.
- Serialización.
- Publicación y consumo de mensajes.
- Integraciones externas.

Para probar REST se puede utilizar Rest Assured:

```java
given()
    .contentType("application/json")
    .body(payload)
.when()
    .post("/api/events")
.then()
    .statusCode(200);
```

Para aislar el controlador de Kafka u otras integraciones:

```java
@InjectMock
OrderService service;
```

Separar pruebas unitarias, pruebas REST y pruebas de integración con infraestructura real.

## 10. Migrar el empaquetado

Spring Boot suele producir un JAR ejecutable único. Quarkus normalmente produce un fast-jar:

```text
target/quarkus-app/quarkus-run.jar
target/quarkus-app/lib/
target/quarkus-app/app/
target/quarkus-app/quarkus/
```

El Dockerfile debe copiar toda la carpeta:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
```

Después comprobar:

```bash
./mvnw clean package -DskipTests
docker build -t mi-aplicacion:local .
docker run --rm -p 8080:8080 mi-aplicacion:local
```

## 11. Validar por etapas

Después de cada grupo de cambios:

```bash
./mvnw clean compile
./mvnw test
./mvnw package -DskipTests
```

Luego validar:

1. Arranque de Quarkus.
2. Endpoints REST.
3. Integraciones externas.
4. Mensajería.
5. Imagen Docker.
6. Pruebas de carga y resiliencia.

## 12. Orden recomendado

```text
1. Crear linea base
2. Cambiar pom.xml
3. Migrar inyeccion CDI
4. Migrar controladores REST
5. Migrar configuracion
6. Migrar servicios y clientes
7. Migrar productores y consumidores
8. Migrar pruebas
9. Actualizar Dockerfile
10. Ejecutar pruebas de integracion
11. Validar la imagen
12. Desplegar
```

## Errores frecuentes

- Cambiar framework y broker simultáneamente.
- Mantener anotaciones Spring después de quitar sus dependencias.
- Copiar solo el JAR principal de Quarkus al contenedor.
- Probar únicamente el arranque del contexto.
- Serializar directamente objetos Avro en respuestas JSON de error.
- No cerrar productores ni consumidores.
- Codificar credenciales o URLs en el código.
- Confundir una compilación exitosa con una integración validada.
- Mantener archivos `application.yml` o propiedades heredadas que Quarkus ya no utiliza.

## Regla principal

Primero migrar Spring Boot a Quarkus manteniendo el comportamiento y las dependencias externas. Después cambiar Kafka por RabbitMQ y, finalmente, preparar el despliegue en AKS. Cada fase debe tener sus propias pruebas y criterios de salida.
