# CoolStore Monolith

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
   -e POSTGRES_USER=${POSTGRES_USER:?set-me} \
   -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD:?set-me} \
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
 data-source add --name=CoolstoreDS --jndi-name=java:jboss/datasources/CoolstoreDS --driver-name=postgresql --connection-url=jdbc:postgresql://127.0.0.1:5432/postgresDB --user-name=${POSTGRES_USER:?set-me} --password=${POSTGRES_PASSWORD:?set-me}
 ```

```
jms-topic add --topic-address=topic.orders --entries=topic/orders

/subsystem=messaging-activemq/server=default:write-attribute(name=cluster-password, value=${CLUSTER_PASSWORD:?set-me})
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
---

## Running CoolStore on Quarkus (post-migration)

The Java EE 7 / JBoss EAP instructions above are retained for the 30-day rollback window
(TG8.8) only. The application now runs on **Quarkus 3.8.1** (Java 11).

### Build and test
```
mvn clean verify
```

### Run locally (dev profile: H2 in-memory + in-memory Kafka)
```
mvn quarkus:dev
```
- Storefront: `http://localhost:8080/` (AngularJS app served from `META-INF/resources`, TG3)
- REST API base path: `/services` (kept for frontend parity, TG3.5) — e.g. `GET /services/products`
- Health: `/q/health/live`, `/q/health/ready` (replaced `health.jsp`, TG3.4)
- Metrics: `/q/metrics` (Micrometer/Prometheus, TG8.4)

### Production profile (%prod: PostgreSQL + Kafka, Flyway-managed schema)
Configure via environment variables — never commit credentials:
`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `QUARKUS_OIDC_AUTH_SERVER_URL`, `QUARKUS_OIDC_CLIENT_ID`,
`QUARKUS_HTTP_CORS_ORIGINS`.

### Migration notes
- WebLogic compatibility layer, WAR descriptors (`web.xml`, `beans.xml`) and the
  `ShippingServiceRemote` interface were removed (TG2); startup hooks now use Quarkus
  `@Observes StartupEvent` (`StartupListener`).
- Security (TG4): catalog browsing is `@PermitAll`; cart and order endpoints require the
  Keycloak realm role `user` (realm `eap`, frontend public client `eap-app`). The realm
  export (`realm-export.json`) includes the `user` realm role for provisioning.
- Container image: multi-stage `Containerfile`, non-root UID (TG6).
- OpenShift manifests: `k8s/` (Deployment, Service, Route, probes, HPA, Kafka topics,
  Tekton pipeline) (TG7).
