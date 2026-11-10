# Quarkus order fulfillment Example

A simple example application demonstrating process-engine-worker setup within a quarkus application.
The example uses Camunda 8.8, Java 17 and Quarkus 3.37.3 or later.

## Getting Started

Prerequisites: JDK 17 or 21, Docker with Compose, and enough memory for Camunda and Elasticsearch (allow at least 4 GB for Docker).

**Start the docker compose stack from this directory**

```shell
docker compose up -d
```

**Build and run the quarkus app**

From the repository root:

```shell
./mvnw -B -ntp -pl quarkus-extension/deployment,examples/quarkus-order-fullfillment -am clean install -DskipITests
java -jar examples/quarkus-order-fullfillment/target/quarkus-app/quarkus-run.jar
```

**Links**

- Operate: [http://localhost:8088/operate](http://localhost:8088/operate)
  - Login with demo demo
- Example App: [http://localhost:8080](http://localhost:8080)


## Example szenarios

Use [order-fulfillment.http](order-fulfillment.http) in an HTTP client.

- Happy Path: Receive Order and fulfill order
- Error Path: Payment failed and order canceled
- Order Delayed: Inform customer on delay and order is fulfilled
