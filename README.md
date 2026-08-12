# CoolStore Monolith - Quarkus Migration

This repository contains the CoolStore Monolith application, migrated from WebLogic/JBoss EAP to Quarkus.

## Prerequisites

* Java 17 or later
* Maven 3.8.5 or later
* Docker or Podman (tested with podman version 4.3.1)

## Quick Start with Dev Mode

Quarkus provides a powerful development mode with hot reload:

```bash
./mvnw quarkus:dev
```

This will:
- Start the application on http://localhost:8080
- Automatically start Kafka via Dev Services (testcontainer)
- Use H2 in-memory database
- Enable live reload for code changes

## Running with PostgreSQL and Kafka

### Start PostgreSQL

```bash
podman run --name myPostgresDb \
   -p 5432:5432 \
   -e POSTGRES_USER=postgresUser \
   -e POSTGRES_PASSWORD=postgresPW \
   -e POSTGRES_DB=postgresDB \
   -d postgres
```

### Start Kafka

```bash
podman run --name kafka \
   -p 9092:9092 \
   -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
   -d quay.io/strimzi/kafka:latest-kafka-3.6.0
```

### Start Keycloak

```bash
podman run --name keycloak \
   -p 8081:8080 \
   -e KEYCLOAK_ADMIN=admin \
   -e KEYCLOAK_ADMIN_PASSWORD=admin \
   quay.io/keycloak/keycloak:latest start-dev
```

Open http://127.0.0.1:8081 and:
1. Login with admin/admin
2. Create a new realm by importing `realm-export.json`
3. Create a user (e.g., "user1") in the "eap" realm
4. Set the user's password

### Build and Run

```bash
# Build the application
./mvnw package

# Run in JVM mode
java -jar target/quarkus-app/quarkus-run.jar

# Or run with Quarkus
./mvnw quarkus:dev
```

Navigate to http://127.0.0.1:8080

![coolstore](assets/coolstore.png "coolstore")

## Docker/Container Deployment

### Build Container Image

```bash
# Build the application
./mvnw package

# Build container image
docker build -f Dockerfile.jvm -t quarkus/coolstore-jvm .

# Run the container
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgresDB \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  quarkus/coolstore-jvm
```

## Configuration

The application is configured via `src/main/resources/application.properties`. Key configurations:

- **Database**: PostgreSQL (production), H2 (dev/test)
- **Messaging**: Kafka for order processing
- **Security**: OIDC/Keycloak integration
- **Port**: 8080 (configurable via `quarkus.http.port`)

### Profile-based Configuration

- **%dev**: Development mode with H2 and Kafka Dev Services
- **%test**: Test mode with H2 in-memory database
- **%prod**: Production mode (default) with PostgreSQL and Kafka

## Features

- **RESTful API**: JAX-RS endpoints for products, orders, and shopping cart
- **Persistence**: JPA/Hibernate with PostgreSQL
- **Messaging**: Reactive messaging with Kafka for order processing
- **Security**: OIDC integration with Keycloak
- **Database Migration**: Flyway for schema management

## API Endpoints

- `GET /services/products` - List all products
- `GET /services/products/{itemId}` - Get product by ID
- `GET /services/cart/{cartId}` - Get shopping cart
- `POST /services/cart/{cartId}/{itemId}/{quantity}` - Add item to cart
- `POST /services/cart/checkout/{cartId}` - Checkout cart
- `GET /services/orders` - List all orders
- `GET /services/orders/{orderId}` - Get order by ID

## Development

### Live Reload

In dev mode (`./mvnw quarkus:dev`), Quarkus automatically reloads on code changes.

### Testing

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ProductServiceTest
```

### Dev UI

Access the Quarkus Dev UI at http://localhost:8080/q/dev

## Migration Notes

This application was migrated from WebLogic/JBoss EAP to Quarkus. See `MIGRATION_NOTES.md` for detailed migration information.

## Architecture

- **Service Layer**: CDI beans with JPA for data access
- **REST Layer**: JAX-RS endpoints
- **Messaging**: Reactive messaging for asynchronous order processing
- **Database**: PostgreSQL with Flyway migrations
- **Security**: OIDC with Keycloak

## Troubleshooting

### Database Connection Issues
Ensure PostgreSQL is running and accessible. Check connection settings in `application.properties`.

### Kafka Connection Issues
Ensure Kafka is running on localhost:9092 or configure `kafka.bootstrap.servers`.

### Keycloak Issues
Ensure Keycloak is running on port 8081 and the "eap" realm is imported.

## License

This project is licensed under the Apache License 2.0.
