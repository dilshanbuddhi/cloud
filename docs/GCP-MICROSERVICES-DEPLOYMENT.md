# GCP microservices deployment (Spring Cloud + Eureka + MIG)

This repository is already structured for **Config Server**, **Eureka**, **API Gateway**, and three services with fixed ports. This guide maps that layout to **Google Compute Engine**, **MIG health checks**, and **production-style Eureka URLs**.

If you split **platform** (Config + Eureka + Gateway) and **microservices** into **two MIGs**, read **`GCP-PLATFORM-AND-SERVICES-MIG.md`** and use **`pm2/ecosystem.platform.config.cjs`** / **`ecosystem.services.config.cjs`**.

## 1. Project structure (this repo)

| Component | Spring `application.name` | Port | Module path |
|-----------|---------------------------|------|-------------|
| Config Server | `config-server` | 8888 | `platform/config-server` |
| Eureka Server | `eureka-server` | 8761 | `platform/eureka-server` |
| API Gateway | `api-gateway` | 8080 | `platform/api-gateway` |
| user-service | `user-service` | 8081 | `services/user-service` |
| product-service | `product-service` | 8082 | `services/product-service` |
| order-service | `order-service` | 8083 | `services/order-service` |

Runtime configuration is mainly under **`config-repo/*.yml`** (served by Config Server). Shared defaults live in **`config-repo/application.yml`**.

## 2. Bind address and health

- **`server.address: 0.0.0.0`** is set in `config-repo` so each process listens on all interfaces (required for GCP external IP and internal LB).
- Actuator **`/actuator/health`** is exposed for **liveness/readiness** (see `application.yml` `management.*`).
- **MIG / HTTP load balancer** health checks should target the **API Gateway**: port **8080**, path **`/actuator/health`**. Microservice ports **8081–8083** do not need to be public if all traffic goes through the gateway.

## 3. Eureka client configuration (no `localhost` for `defaultZone` in production)

`config-repo/application.yml` sets:

```yaml
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
```

**On GCP VMs**, set environment variable **`EUREKA_URL`** to a URL every client can reach:

- **Single VM (simplest):** `http://<VM_EXTERNAL_IP>:8761/eureka`
- **Dedicated Eureka VM / LB:** `http://<eureka-host-or-LB>:8761/eureka`
- **Same VM only:** `http://127.0.0.1:8761/eureka` works for registration but does not satisfy a “no localhost in production” policy; use **loopback only for demos** if allowed.

`PM2` / systemd should export `EUREKA_URL` before starting JVMs. Example file: **`deploy/gcp-vm.env`** (see `deploy/gcp-vm.env.example`).

**Profiles:** On GCP set **`SPRING_PROFILES_ACTIVE=prod`** so Config Server serves **`config-repo/application-prod.yml`**, where Eureka `defaultZone` is **`${EUREKA_URL}` only** (no localhost fallback). Locally, omit `prod` so **`application.yml`** keeps **`http://127.0.0.1:8761/eureka`** when `EUREKA_URL` is unset.

### Registration address (`prefer-ip-address`)

With **`prefer-ip-address: true`**, instances usually register with their **detected IP**. On GCE that is often the **internal** `10.x` address, which is correct for **east-west** calls inside the VPC. If peers cannot reach that IP, set:

- **`eureka.instance.hostname`** or **`eureka.instance.ip-address`** via env/config (advanced; see Spring Cloud Netflix docs).

## 4. API Gateway routing

Gateway routes are in **`config-repo/api-gateway.yml`** (prefixes `/auth/**`, `/users/**`, `/products/**`, `/orders/**` → `lb://user-service`, etc.). No change is required for GCP if Eureka sees all services.

## 5. Maven executable JARs

Each Spring Boot module uses **`spring-boot-maven-plugin`** with the **`repackage`** goal so **`java -jar target/*.jar`** works. Build from repo root:

```bash
mvn clean package -DskipTests
```

Or **`bash scripts/build-all.sh`**.

## 6. Firewall rules (VPC)

| Port(s) | Purpose |
|---------|---------|
| 22 | SSH |
| 8888 | Config Server (restrict to admin / internal in production) |
| 8761 | Eureka dashboard & client registry |
| 8080 | API Gateway (users + LB health checks) |
| 8081–8083 | Microservices (optional if gateway-only access) |

For **Google HTTP(S) load balancer** health probes, allow **`130.211.0.0/22`** and **`35.191.0.0/16`** to **TCP 8080** on tagged instances (see `scripts/gcp-mig-lb-setup.sh`).

## 7. Instance template startup (IaaS)

Use **`scripts/gcp-instance-startup.sh`** as GCE **startup script** metadata (`startup-script` or `startup-script-url`).

**One-shot template create** (metadata + startup script merged; run from cloned repo root on Cloud Shell):

```bash
gcloud config set project YOUR_PROJECT_ID
chmod +x scripts/create-gce-template-minipos.sh
./scripts/create-gce-template-minipos.sh
```

Override repo URL or region: `ECA_REPO_URL=https://github.com/you/other.git REGION=europe-west1 ./scripts/create-gce-template-minipos.sh`  
If `instance-template-minipos` already exists: `gcloud compute instance-templates delete instance-template-minipos`

Equivalent raw `gcloud` (replace `/path/to/Cloud` with your clone path):

```bash
gcloud compute instance-templates create instance-template-minipos \
  --region=asia-south1 \
  --machine-type=e2-medium \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --tags=eca-gateway,http-server \
  --scopes=https://www.googleapis.com/auth/cloud-platform \
  --metadata=eca-repo-url=https://github.com/dilshanbuddhi/cloud.git,eca-repo-branch=main \
  --metadata-from-file=startup-script=/path/to/Cloud/scripts/gcp-instance-startup.sh
```

**Metadata keys:**

| Key | Description |
|-----|-------------|
| `eca-repo-url` | Git URL to clone |
| `eca-repo-branch` | Branch (default `main`) |
| `eureka-url` | Optional full `http://host:8761/eureka`; if omitted, uses this instance’s **external IP** |
| `config-server-url` | Default `http://127.0.0.1:8888` |
| `eca-skip-build` | `1` on golden images with pre-built JARs |
| `eca-app-dir` | App root (default `/opt/eca-cloud`) |

**Attach instance template** with network tags matching your firewall rules (e.g. `eca-gateway`, `http-server`).

After first boot:

```bash
sudo pm2 status
sudo pm2 logs
curl -s http://127.0.0.1:8761/
curl -s http://127.0.0.1:8080/actuator/health
```

Persist PM2 on reboot (startup script already runs **`pm2 startup systemd`**; verify with `sudo systemctl status pm2-root`).

## 8. JAR-only run order (manual / `nohup`)

1. Config Server (needs `CONFIG_REPO_PATH` to native repo path)  
2. Eureka  
3. API Gateway + microservices (any order after Eureka is up)

`scripts/gcp-vm-run.sh` and **`pm2/ecosystem.config.cjs`** encapsulate order and env.

## 9. Docker option (PaaS / containers elsewhere)

See **`deploy/docker/README.md`**. Docker is optional; the assignment stack often uses **VM + PM2**.

## 10. Troubleshooting

### Services do not appear in Eureka

1. **`EUREKA_URL` wrong:** Check `deploy/gcp-vm.env` / PM2 env: must match reachable Eureka (`curl http://<host>:8761/eureka/apps`).
2. **Config Server not ready:** Clients fail if `CONFIG_SERVER_URL` is wrong or Config starts after clients — start **Config before Eureka clients** (PM2 order in `ecosystem.config.cjs` is config → eureka → others).
3. **Firewall:** Port **8761** open between client VM and Eureka (and **8888** for Config if used).
4. **Wrong JAR:** “no main manifest attribute” → rebuild with **`mvn clean package`** after `repackage` fix in `pom.xml`.

### Health check unhealthy in MIG

1. Path **`/actuator/health`**, port **8080** (gateway).  
2. Firewall allows **GCP probe ranges** to **8080**.  
3. Gateway process up: `curl -v http://127.0.0.1:8080/actuator/health` on the VM.

### Gateway 503 / no route

Eureka has no instance for service name — fix registration; check **`spring.application.name`** in each `config-repo/*.yml` matches gateway `DiscoveryClient` names.

### Cloud SQL / Firestore / GCS errors

VM **service account** or **`GOOGLE_APPLICATION_CREDENTIALS`**; **`user-service.yml`** JDBC URL; Firestore project id in YAML — see `docs/GCP-VM-DEPLOYMENT.md`.

## 11. Common mistakes

| Mistake | Fix |
|---------|-----|
| `defaultZone` still `localhost` on VMs | Set **`EUREKA_URL`** |
| Only `127.0.0.1` bind | Ensure **`server.address: 0.0.0.0`** from Config / `config-repo` |
| Plain JAR after build | Use **`repackage`** in **`spring-boot-maven-plugin`** |
| Health check on wrong port | Use **8080** for gateway-facing LB |
| Opening 8081–8083 to world | Prefer **gateway-only** public access |

## 12. Related scripts

| File | Role |
|------|------|
| `scripts/gcp-instance-startup.sh` | Template/MIG first boot |
| `scripts/gcp-vm-run.sh` | One-shot run on Linux VM |
| `scripts/gcp-mig-lb-setup.sh` | Firewall + health check + optional LB |
| `pm2/ecosystem.config.cjs` | Process manager, autorestart |
