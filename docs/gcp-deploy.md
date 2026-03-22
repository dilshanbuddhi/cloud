# GCP Deployment Checklist (VM + PM2, no containers)

This is a practical checklist for the required architecture: Config Server + Eureka + API Gateway + microservices, running on Compute Engine VMs via PM2.

## 0) Decide topology

Minimum (easy, still meets HA requirement if you use MIGs):

- One Managed Instance Group (MIG) per app:
  - `config-server-mig` (2 instances)
  - `eureka-server-mig` (2 instances)
  - `api-gateway-mig` (2 instances) behind an external HTTP(S) Load Balancer
  - `user-service-mig` (2 instances)
  - `product-service-mig` (2 instances)
  - `order-service-mig` (2 instances)

If you are time-limited, you can run multiple apps on the same VM, but the assignment expects platform components and services to be horizontally scalable (MIG + autoscaling).

## 1) VPC, firewall, NAT

- Create a VPC + subnet(s).
- Create firewall rules:
  - Allow health checks to Gateway instances.
  - Allow internal traffic between VMs on required ports.
- If instances need outbound internet (download deps, reach Mongo/Cloud Storage APIs), configure Cloud NAT.

Ports used by this project (default):

- Config Server: 8888
- Eureka: 8761
- API Gateway: 8080
- User: 8081
- Product: 8082
- Order: 8083

Actuator health endpoint used for health checks:

- `GET /actuator/health`

Recommended health endpoints:

- For load balancers: `GET /actuator/health/readiness`
- For autohealing (VM health): `GET /actuator/health/liveness`

Quick verification commands (run from your laptop or inside the VPC):

```bash
curl -fsS http://<gateway-host>:8080/actuator/health | jq
curl -fsS http://<gateway-host>:8080/actuator/health/readiness | jq
curl -fsS http://<gateway-host>:8080/actuator/health/liveness | jq

curl -fsS http://<eureka-host>:8761/actuator/health | jq
curl -fsS http://<config-host>:8888/actuator/health | jq
```

Business endpoint smoke tests (through the Gateway):

```bash
curl -fsS http://<gateway-host>:8080/products | jq
curl -fsS "http://<gateway-host>:8080/orders?userId=1" | jq
```

## 2) Datastores

- Relational DB (Cloud SQL): for `user-service`
  - Set `USER_DB_URL`, `USER_DB_USERNAME`, `USER_DB_PASSWORD`
- NoSQL DB (Mongo): for `product-service` and `order-service`
  - Set `PRODUCT_MONGO_URI`, `ORDER_MONGO_URI`

## 3) Cloud Storage (mandatory)

- Create a bucket for product images.
- Create a service account with minimum permissions for object write/read.
- Put the JSON key on the VM image (do NOT commit it to git).
- Set:
  - `STORAGE_PROVIDER=gcs`
  - `GCS_PROJECT_ID=...`
  - `GCS_BUCKET=...`
  - `GCS_CREDENTIALS_PATH=/opt/app/keys/service-account.json`

## 4) Build artifacts

Build all runnable JARs (JDK 25 required):

```bash
scripts/build-all.cmd
```

Expected JAR paths (examples):

- `platform/config-server/target/config-server-0.1.0-SNAPSHOT.jar`
- `platform/eureka-server/target/eureka-server-0.1.0-SNAPSHOT.jar`
- `platform/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar`
- `services/user-service/target/user-service-0.1.0-SNAPSHOT.jar`
- `services/product-service/target/product-service-0.1.0-SNAPSHOT.jar`
- `services/order-service/target/order-service-0.1.0-SNAPSHOT.jar`

## 5) VM image

Bake an image that includes:

- JDK 25
- Node.js + PM2
- This repository under `/opt/app`

You can build the image from a "golden" VM, then create a disk image from it.

## 6) PM2

From `/opt/app` on the VM:

```bash
pm2 start pm2/ecosystem.config.cjs
pm2 save
pm2 startup
```

Logs (as configured in `pm2/ecosystem.config.cjs`) go to `/opt/app/logs/*.log`.

Verify:

```bash
pm2 monit
```

## 7) Config Server source

This repo uses the Config Server "native" backend (filesystem).

- Ensure the config repo exists on the VM.
- Set `CONFIG_REPO_PATH=/opt/app/config-repo`.

## 8) Instance templates + MIGs

- Create an instance template per app (or reuse a common template) that:
  - Uses your baked image
  - Sets required environment variables
  - Starts PM2 on boot

Boot startup-script example (metadata):

```bash
#!/usr/bin/env bash
set -euo pipefail

cd /opt/app

# Ensure PM2 resurrects previously saved processes on reboot
pm2 resurrect || true
```
- Create a MIG per app:
  - Min instances: 2
  - Enable autohealing with health check
  - Enable autoscaling (CPU-based or request-based)

## 9) Load Balancer + DNS

- Put an external HTTP(S) Load Balancer in front of the API Gateway MIG.
- Configure a backend health check to `GET /actuator/health` on port 8080.
- Add Cloud DNS record -> LB IP.

## 10) Eureka dashboard URL (mandatory)

Expose the Eureka dashboard (public URL) and put it in `README.md`:

- `http://<public-ip-or-dns>:8761`

If Eureka is internal-only, you can still expose it via a separate LB or a temporary firewall rule for marking (be careful with security).

## 11) Screen recording (mandatory)

- Show required GCP resources (MIGs, templates, LB, health checks, DNS, NAT, SQL, Storage, firewall rules, etc.)
- SSH into each VM and run:
  - `pm2 monit`
