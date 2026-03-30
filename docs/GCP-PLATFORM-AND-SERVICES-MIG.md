# Platform MIG + Services MIG (dual instance groups)

Use this when you run **one Managed Instance Group (MIG) for the platform** (Config, Eureka, API Gateway) and **another MIG for microservices** (user, product, order).

## What you need next (checklist)

### 1. Networking

- Both MIGs in the **same VPC** (same region or peered subnets).
- **Firewall**
  - **Service VMs → Platform VMs:** allow TCP **8888** (Config), **8761** (Eureka), and **8081–8083** optional only if gateway calls instance IPs directly (usually via Eureka; gateway runs on **platform** VMs).
  - **Platform VM → Service VMs:** allow TCP **8081–8083** (gateway → services).
  - **Internet → Platform MIG:** TCP **8080** (public load balancer / health checks).
  - **GCP LB health probes:** allow `130.211.0.0/22` and `35.191.0.0/16` to **8080** on platform instances (see `scripts/gcp-mig-lb-setup.sh`).

### 2. Environment on each tier

**Platform VMs** (`deploy/gcp-vm.env` or metadata):

| Variable | Typical value |
|----------|-----------------|
| `CONFIG_SERVER_URL` | `http://127.0.0.1:8888` (Config on same VM) |
| `EUREKA_URL` | `http://<PLATFORM_IP_OR_ILB>:8761/eureka` — must be reachable by **service VMs** (use **internal ILB DNS** or a stable internal IP). |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `CONFIG_REPO_PATH` | `/opt/eca-cloud/config-repo` (or your path) |

**Service VMs:**

| Variable | Sample |
|----------|--------|
| `CONFIG_SERVER_URL` | `http://10.x.x.x:8888` or `http://internal-lb-config.example:8888` |
| `EUREKA_URL` | `http://10.x.x.x:8761/eureka` or `http://internal-lb-eureka.example:8761/eureka` |
| `SPRING_PROFILES_ACTIVE` | `prod` |

Do **not** use `localhost` for `CONFIG_SERVER_URL` / `EUREKA_URL` on **service** VMs unless Config and Eureka run on that same machine.

### 3. PM2 on each tier

- **Platform template:** set instance metadata **`eca-pm2-ecosystem=platform`** so startup uses **`pm2/ecosystem.platform.config.cjs`** (Config + Eureka + Gateway only).
- **Services template:** metadata **`eca-pm2-ecosystem=services`** → **`pm2/ecosystem.services.config.cjs`**.

Single-VM / lab: omit metadata or use **`all`** → **`pm2/ecosystem.config.cjs`** (everything on one box).

Both templates still need the **full repo** (or golden image with all JARs built) so paths like `platform/...` and `services/...` exist.

### 4. Load balancing

| Traffic | Target | Port | Health check |
|---------|--------|------|----------------|
| **Users + Mini POS UI** (API Gateway serves `/`) | **Platform MIG** | **8080** | `GET /actuator/health` |
| **Config** (microservices only) | Platform MIG (often **internal**, not public) | **8888** | `GET /actuator/health` |
| **Eureka** (clients only) | Platform MIG (often **internal**) | **8761** | Eureka HTTP `/` or actuator if exposed |

**Recommended for coursework:**

1. One **external HTTP(S) load balancer** → backend service → **Platform MIG**, named port **`http:8080`**. This is the **only public URL** users need for **API + embedded frontend** on the gateway.
2. **Config + Eureka:** either  
   - **Internal TCP/HTTP load balancer(s)** toward the **platform** MIG on **8888** / **8761**, or  
   - Documented **static internal IP** of one healthy platform instance (simpler, weaker for HA).

**Frontend (course PaaS requirement):** deploy the web UI separately on **Cloud Run** and set **`API_BASE_URL`** to the **public gateway LB URL** (same host users use for `/auth`, `/products`, …).

### 5. Eureka and scaling

- If **platform MIG** has **multiple** instances, each runs Eureka unless you change the template — you then need **Eureka peer replication** (see Spring Cloud docs). Easiest for marks: **platform MIG size ≥ 1** with ILB, or peers once peers are configured in `config-repo`.
- **Service MIG** can scale horizontally if every instance registers with Eureka and **security groups** allow the gateway to reach **8081–8083** on service instances.

### 6. Order of operations (first deploy)

1. Boot **one platform instance**; verify `http://<internal>:8761` and `http://<internal>:8888/actuator/health`.
2. Set **service** `gcp-vm.env` with that **internal** Config + Eureka URL (or ILB hostname).
3. Boot **service** MIG; check Eureka dashboard for **user-service**, **product-service**, **order-service**.
4. Verify gateway: `http://<platform-external-or-LB>:8080/actuator/health` and a sample `/auth` or `/products` call.
5. Attach **external LB** to platform MIG **:8080**; add **Cloud DNS** A/AAAA record to LB IP if required.
6. Point **Cloud Run** frontend at the LB URL if UI is not only on the gateway.

### 7. Metadata for instance templates

| Key | Platform MIG | Services MIG |
|-----|----------------|---------------|
| `eca-repo-url` | same repo URL | same |
| `eca-pm2-ecosystem` | `platform` | `services` |
| `eureka-url` | optional; script can use external IP | set to **platform** Eureka base URL |
| `config-server-url` | default `http://127.0.0.1:8888` | `http://<platform-host>:8888` |

For **services** startup, you may need custom metadata or a small wrapper script to write `deploy/gcp-vm.env` with the right `CONFIG_SERVER_URL` / `EUREKA_URL` before PM2 starts.

## Related files

- `pm2/ecosystem.platform.config.cjs` — platform processes only  
- `pm2/ecosystem.services.config.cjs` — microservices only  
- `pm2/ecosystem.config.cjs` — all-in-one  
- `scripts/gcp-instance-startup.sh` — reads `eca-pm2-ecosystem`  
- `scripts/gcp-mig-lb-setup.sh` — firewall + health check + optional external LB shell

## Common mistakes

- Service VMs still using **localhost** for Config/Eureka → timeouts, not registered in Eureka.
- Only one MIG firewall opened → gateway **503** on `lb://user-service`.
- Health check on **8081** instead of **8080** → LB marks platform unhealthy.
- Both MIGs running **full** `ecosystem.config.cjs` → duplicate Eureka/Config on service VMs.
