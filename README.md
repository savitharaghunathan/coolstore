# CoolStore Monolith

This repository contains the CoolStore monolith application, now migrated to Quarkus 3.

## Quarkus Migration (Current)

This application has been migrated from Java EE 7 to Quarkus 3. The following instructions describe how to run the Quarkus version.

### Prerequisites

* Java 17 or higher
* Maven 3.8.5 or higher
* PostgreSQL database
* AMQP broker (Apache Artemis or RabbitMQ)

### Running the Application

#### Development Mode (with hot reload)

```bash
mvn quarkus:dev
```

The application will start on http://localhost:8080

#### Building the Application

```bash
mvn clean package
```

The packaged application will be available in `target/quarkus-app/`

#### Running the Packaged Application

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

#### Building a Native Executable

```bash
mvn clean package -Pnative
```

Then run with:

```bash
./target/ROOT-1.0.0-SNAPSHOT-runner
```

### Configuration

The application is configured via `src/main/resources/application.properties`. Key settings include:

**Database Configuration:**
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstoredb
```

**Messaging Configuration:**
```properties
mp.messaging.incoming.orders.connector=smallrye-amqp
mp.messaging.incoming.orders.address=orders
mp.messaging.outgoing.orders-out.connector=smallrye-amqp
mp.messaging.outgoing.orders-out.address=orders
```

### Required Services

#### Start PostgreSQL

```bash
podman run --name coolstore-postgres \
   -p 5432:5432 \
   -e POSTGRES_USER=coolstore \
   -e POSTGRES_PASSWORD=coolstore \
   -e POSTGRES_DB=coolstoredb \
   -d postgres
```

#### Start Apache Artemis (AMQP Broker)

```bash
podman run --name artemis \
   -p 5672:5672 \
   -p 8161:8161 \
   -e AMQ_USER=admin \
   -e AMQ_PASSWORD=admin \
   -d quay.io/artemiscloud/activemq-artemis-broker
```

### Accessing the Application

* Application: http://localhost:8080
* REST API: http://localhost:8080/services
* Health Check: http://localhost:8080/q/health

### API Endpoints

* `GET /services/products` - List all products
* `GET /services/products/{itemId}` - Get product by ID
* `GET /services/cart/{cartId}` - Get shopping cart
* `POST /services/cart/{cartId}/{itemId}/{quantity}` - Add item to cart
* `POST /services/cart/checkout/{cartId}` - Checkout cart
* `GET /services/orders` - List all orders
* `GET /services/orders/{orderId}` - Get order by ID

### Migration Notes

This application was migrated from Java EE 7/WebLogic to Quarkus 3 with the following major changes:

* **Packaging:** WAR → JAR
* **EJB → CDI:** All `@Stateless`, `@Stateful` beans converted to `@ApplicationScoped`
* **JMS → Reactive Messaging:** MDBs converted to `@Incoming` methods using SmallRye Reactive Messaging
* **JNDI Removed:** Direct CDI injection replaces all JNDI lookups
* **Namespace:** `javax.*` → `jakarta.*` for Jakarta EE APIs
* **Lifecycle:** WebLogic lifecycle listeners → Quarkus `StartupEvent`/`ShutdownEvent`
* **Configuration:** `persistence.xml` + server config → `application.properties`

---

## Legacy Java EE 7 Deployment (Deprecated)

This repository has the complete coolstore monolith built as a Java EE 7 application. To deploy it on JBoss 7.4 follow the instructions below


## Pre requisite

* JBoss 7.4 zip installation
* Keycloak v20.0.5 zip installation
* podman or docker, tested with podman version 4.3.1
* maven, tested with maven version 3.8.5
* OpenJDK, tested with version 17.0.5

## Start a postgreSQL database

```
podman run --name myPostgresDb \
   -p 5432:5432 \
   -e POSTGRES_USER=postgresUser \
   -e POSTGRES_PASSWORD=postgresPW \
   -e POSTGRES_DB=postgresDB \
   -d postgres
```

## Start keycloak

Extract keycloak-20.0.5.zip

```cd keycloak-20.0.5```

Start keycloak in dev mode listening on port 8081

``` ./bin/kc.sh start-dev --http-port=8081 ```

Open http://127.0.0.1:8081 in your browser


Set an administrator username and password, then login to keycloak using these credentials

Click on the "Master" dropdown, and select "Create Realm"

Click on "Browse" and locate the file realm-export.json in this repo.

Click on "Create" to create the "eap" realm

Click on "Users" and "Create new user"

Enter a username, e.g. "user1" and click on "Create"

From the next form, click on the "Credentials" tab and "Set password"

Set a password and password confirmation, and unselect "Temporary"

Click on "Save" to store the password.

Keycloak is now configured correctly

## Configure JBoss 7.4

Unzip jboss-eap-7.4.0.zip

``` cd jboss-eap-7.4/jboss-eap-7.4 ```

Create the folder modules/org/postgresql/main

``` mkdir -p modules/org/postgresql/main ```


Download the postgres jdbc driver from https://jdbc.postgresql.org/download/  e.g. https://jdbc.postgresql.org/download/postgresql-42.5.4.jar

 Copy postgres jar file to modules/org/postgresql/main

create module.xml  -- ensure the filename matches the filename downloaded in the previous step

```
cat <<EOF > modules/org/postgresql/main/module.xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.0" name="org.postgresql">
 <resources>
 <resource-root path="postgresql-42.5.4.jar"/>
 </resources>
 <dependencies>
 <module name="javax.api"/>
 <module name="javax.transaction.api"/>
 </dependencies>
</module>
EOF
```


Start JBoss EAP 7.4 in full high availability mode

From the jboss-eap-7.4 folder run:

```./jboss-eap-7.4-2/bin/standalone.sh -c standalone-full-ha.xml  -Djboss.node.name=node1 ```

In another terminal, start the jboss cli, from the jboss7.4 installation folder run 

```  ./bin/jboss-cli.sh --connect ```

Run the following commands:

```
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql)
```

```
 data-source add --name=CoolstoreDS --jndi-name=java:jboss/datasources/CoolstoreDS --driver-name=postgresql --connection-url=jdbc:postgresql://127.0.0.1:5432/postgresDB --user-name=postgresUser --password=postgresPW
 ```

```
jms-topic add --topic-address=topic.orders --entries=topic/orders

/subsystem=messaging-activemq/server=default:write-attribute(name=cluster-password, value=password)
```

## Build and deploy the application

From the root of this repo, run: 

`mvn package`

Set the JBOSS_HOME env variable e.g. 

`export JBOSS_HOME=~/jboss-eap-7.4/jboss-eap-7.4`

Run the jboss cli: 

` $JBOSS_HOME/bin/jboss-cli.sh`

Run the following command to deploy the application:

 `deploy ./target/ROOT.war`

Navigate to http://127.0.0.1:8080

![coolstore](assets/coolstore.png "coolstore")

From the coostore, click on "Sign in" in the top right

Login with the user credentials created on Keycloak, e.g. user1

You should now be able to complete the checkout process.

## Start a second instance.

Make a copy of the jboss-eap-7.4/jboss-eap-7.4 e.g jboss-eap-7.4/jboss-eap-7.4-2

Within the jboss-eap-7.4 folder you should now see jboss-eap-7.4 and jboss-eap-7.4-2

In the jboss-eap-7.4-2, remove the contents of standalone/data/activemq

From the jboss-eap-7.4 folder run:

`./jboss-eap-7.4-2/bin/standalone.sh -c standalone-full-ha.xml -Djboss.socket.binding.port-offset=100  -Djboss.node.name=node2`

## Monitor the logs

Open up two terminals in the jboss-eap-7.4 folder

Run:

`tail -f jboss-eap-7.4/standalone/log/server.log` 

in one terminal and 

`tail -f jboss-eap-7.4-2/standalone/log/server.log` 

in the other

## Testing clustering

If you perform a test checkout of an item, you should see both nodes processing the messages in the logs e.g.

```
2023-03-02 14:41:23,131 INFO  [stdout] (Thread-3 (ActiveMQ-client-global-threads)) 
2023-03-02 14:41:23,131 INFO  [stdout] (Thread-3 (ActiveMQ-client-global-threads)) Message recd !
2023-03-02 14:41:23,131 INFO  [stdout] (Thread-3 (ActiveMQ-client-global-threads)) Received order: {"orderValue":10.49,"customerName":"Karl Svensson","customerEmail":"karl@gmail.com","retailPrice":10.0,"discount":-2.5,"shippingFee":2.99,"shippingDiscount":0.0,"items":[{"productSku":"329299","quantity":1}]}
2023-03-02 14:41:23,132 INFO  [stdout] (Thread-3 (ActiveMQ-client-global-threads)) Order object is Order [orderId=0, customerName=Karl Svensson, customerEmail=karl@gmail.com, orderValue=10.49, retailPrice=10.0, discount=-2.5, shippingFee=2.99, shippingDiscount=0.0, itemList=[OrderItem [productId=329299, quantity=1]]]


```