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
