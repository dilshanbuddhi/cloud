# Docker (optional)

The primary deployment for this project is **GCE VM + PM2 + JAR** (see `docs/GCP-MICROSERVICES-DEPLOYMENT.md`). Docker is optional for local experiments or if your course allows containerized runtimes on IaaS.

## Build one service (example: API Gateway)

From **repository root** after `mvn package`:

```bash
docker build -f deploy/docker/Dockerfile.api-gateway -t eca/api-gateway:local .
docker run --rm -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka \
  -p 8080:8080 eca/api-gateway:local
```

`CONFIG_SERVER_URL` / `EUREKA_URL` must point to **reachable** Config and Eureka hosts (not `localhost` inside the container unless you use host networking).

## Repeat for other modules

Copy `Dockerfile.api-gateway`, change `ARTIFACT_PATH` / `EXPOSE` / port to match:

| Service        | JAR path (after build)                    | Port |
|----------------|-------------------------------------------|------|
| config-server  | `platform/config-server/target/...jar`    | 8888 |
| eureka-server  | `platform/eureka-server/target/...jar`     | 8761 |
| api-gateway    | `platform/api-gateway/target/...jar`     | 8080 |
| user-service   | `services/user-service/target/...jar`    | 8081 |
| product-service| `services/product-service/target/...jar`   | 8082 |
| order-service  | `services/order-service/target/...jar`    | 8083 |

Compose is left as an exercise: start **config** and **eureka** first, then gateway and microservices.
