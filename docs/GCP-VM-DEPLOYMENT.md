# GCP VM deployment checklist (e.g. `35.194.27.132`)

Use this before/after copying the repo to a **Google Compute Engine** VM.

## Is the project “ready”?

| Area | Status | Notes |
|------|--------|--------|
| **Build** | OK | JDK **25** + Maven; `mvn -f <module>/pom.xml -DskipTests package` per service |
| **Single-VM layout** | OK | Config → Eureka → Gateway + microservices; PM2 `ecosystem.config.cjs` |
| **Config Server** | OK | Native `config-repo`; set `CONFIG_REPO_PATH` on VM if path differs |
| **Eureka** | OK | `config-repo/application.yml` has `eureka.instance.prefer-ip-address: true` |
| **Gateway + UI** | OK | Mini POS at `http://<VM_IP>:8080/`; CORS `*` for APIs |
| **MySQL (user-service)** | Verify | `user-service.yml` points to Cloud SQL / VM IP; **VM must be in authorized networks** or use **Cloud SQL Auth Proxy** |
| **Firestore / GCS** | Verify | VM service account or `GOOGLE_APPLICATION_CREDENTIALS`; roles: Firestore + Storage |
| **Secrets in git** | Risk | DB password & JWT in `config-repo` — move to **env vars** or **Secret Manager** for production |

## Order service: “Unexpected error” on VM but OK on localhost

`order-service` uses **Firestore**. On localhost you often use **Application Default Credentials** from `gcloud auth application-default login` or a JSON key. On a **GCE VM** you must either:

- Attach a **service account** to the VM with **Cloud Datastore User** (or broader Firestore) + correct **project** (`FIRESTORE_PROJECT_ID` / `firestore.project-id`), or  
- Set **`GOOGLE_APPLICATION_CREDENTIALS`** to a service-account JSON that can access that Firestore project.

Wrong/missing credentials usually throw **PERMISSION_DENIED** or **Unauthenticated** — the API now returns that message in the JSON `message` field (and logs the stack trace).  
Also ensure **`FIRESTORE_PROJECT_ID`** matches the project where your `orders` collection lives (local `application.yml` default matches `config-repo`: `buddhi-cloud`).

## Listen on all interfaces (external IP)

Shared `config-repo/application.yml` sets `server.address: 0.0.0.0` so each service accepts traffic on the VM **external** IP (e.g. `35.194.27.132`), not only `127.0.0.1`. Local `application.yml` files under each module match this for runs without Config Server.

You still need a **VPC firewall rule** allowing TCP to the ports you expose (at minimum **8080** for the gateway).

## GCP firewall (VPC)

Allow inbound (adjust source IPs for production):

| Port | Service |
|------|---------|
| **22** | SSH |
| **8080** | API Gateway (users hit this) |
| **8761** | Eureka (optional external; can lock to internal only) |
| **8888** | Config Server (optional external; prefer internal only) |

Microservice ports **8081–8083** do not need to be public if **only** the gateway is used.

## Environment on the VM

Same machine = you can keep:

- `CONFIG_SERVER_URL=http://127.0.0.1:8888`
- `EUREKA_URL=http://127.0.0.1:8761/eureka`

Or use the public IP (works if firewall allows):

- `CONFIG_SERVER_URL=http://35.194.27.132:8888`
- `EUREKA_URL=http://35.194.27.132:8761/eureka`

Copy and edit:

```bash
cp deploy/gcp-vm.env.example deploy/gcp-vm.env
# edit deploy/gcp-vm.env, then:
set -a && source deploy/gcp-vm.env && set +a   # bash
# PowerShell: Get-Content deploy/gcp-vm.env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') } }
pm2 start pm2/ecosystem.config.cjs
```

## Build order on VM

1. Install **JDK 25**, **Maven**, **PM2** (or use `systemd` instead of PM2).
2. Clone/copy repo; ensure `config-repo/` exists next to the layout expected by `CONFIG_REPO_PATH`.
3. Build jars:

```bash
mvn -f platform/config-server/pom.xml -DskipTests package
mvn -f platform/eureka-server/pom.xml -DskipTests package
mvn -f platform/api-gateway/pom.xml -DskipTests package
mvn -f services/user-service/pom.xml -DskipTests package
mvn -f services/product-service/pom.xml -DskipTests package
mvn -f services/order-service/pom.xml -DskipTests package
```

4. `mkdir -p logs`
5. Start **config-server** first, then **eureka**, then the rest (PM2 starts all; ensure `ecosystem` start order or use `pm2 start` delays if needed).

## One-command run (GCP SSH terminal)

මට ඔබේ VM terminal එක **direct** open කරන්න බැහැ. Repo එක VM එකට copy/git කරලා SSH ඇතුළට:

```bash
cd /path/to/Cloud    # your clone path
chmod +x scripts/gcp-vm-run.sh
./scripts/gcp-vm-run.sh
```

This will: build all jars (unless `SKIP_BUILD=1`), start Config → Eureka → services → Gateway, tail-ready health checks.  
Logs: `logs/*.log`. Stop: `STOP_ONLY=1 ./scripts/gcp-vm-run.sh`.  
With PM2: `npm i -g pm2` then `USE_PM2=1 ./scripts/gcp-vm-run.sh`.

Optional env file: `cp deploy/gcp-vm.env.example deploy/gcp-vm.env` then edit and `source` before the script (see comments in `scripts/gcp-vm-run.sh`).

## Smoke test after deploy

```bash
curl -s http://127.0.0.1:8888/actuator/health
curl -s http://127.0.0.1:8761/actuator/health
curl -s http://127.0.0.1:8080/actuator/health
```

From your laptop:

```text
http://35.194.27.132:8080/
```

## Your VM IP

Example public IP used in this doc: **`35.194.27.132`**. Replace if the VM gets a new IP.

## Load balancer + managed instance group (MIG)

Use an **external HTTP(S) load balancer** on port **80** that forwards to instances on **8080** (API Gateway). Health checks should call **`GET /actuator/health`** on port **8080** (Spring Boot actuator on the gateway).

1. **Firewall:** allow **`130.211.0.0/22`** and **`35.191.0.0/16`** to **`tcp:8080`** on instances with tag **`eca-gateway`** (Google health checks and LB → backend traffic).
2. **Golden image (recommended):** deploy once on a single VM with `scripts/gcp-vm-run.sh` (or PM2), verify `curl -s http://127.0.0.1:8080/actuator/health`, then stop the VM and **create a custom image** from its boot disk. Use that image in an **instance template** so new instances boot with JDK 25, built jars, and repo layout already present.
3. **Managed instance group:** create an MIG from the template, set **named port** `http:8080`, attach it to a **global backend service** with the HTTP health check above.
4. **Frontend:** the gateway serves the Mini POS UI from **`/`** with **relative** API paths — it works through the LB without code changes. If you use the standalone **`frontend/index.html`**, keep the API base **same-origin** (empty string) when not using a dev server on port 3000 so calls go to the LB hostname, not `localhost`.

Helper script (from repo root, Cloud Shell or WSL with `gcloud`):

```bash
chmod +x scripts/gcp-mig-lb-setup.sh
# Creates firewall rule + health check; prints gcloud for MIG/LB unless you set CREATE_LB=1
./scripts/gcp-mig-lb-setup.sh
# After you have a custom image:
export GOLDEN_IMAGE="projects/YOUR_PROJECT/global/images/eca-cloud-v1"
CREATE_LB=1 ./scripts/gcp-mig-lb-setup.sh
```

**Multi-VM note:** each instance currently runs its own Config Server and Eureka; for several backends behind a LB you typically move MySQL to **Cloud SQL**, and align service discovery (single Eureka tier or replace with LB-only routing). Starting with **MIG size 1** plus LB is a valid first step.
