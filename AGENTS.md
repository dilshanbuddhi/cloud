# Agent Instructions (Repository)

This file is for agentic coding assistants working in this repository.

## Quick Orientation

- Multi-module Maven project: root `pom.xml` (`eca-cloud`) is the **parent** for all services (Java **25** + `maven-compiler-plugin` via `pluginManagement`). Build from repo root: `mvn clean package -DskipTests`.
- Each service still has its own `pom.xml` under `platform/` or `services/`.
- Shared runtime configuration is served via Spring Cloud Config from `config-repo/`.
- **GCP VM deploy:** see `docs/GCP-VM-DEPLOYMENT.md` and `deploy/gcp-vm.env.example` (e.g. public IP `35.194.27.132`, gateway on port **8080**).

## Build / Run / Lint / Test

### Java + Maven

- Java: this repo targets **Java 25**. `maven-enforcer-plugin` requires **JDK 25 or newer** (set `JAVA_HOME` to JDK 25). For older JDKs only, lower `<java.version>` and the enforcer `<version>` range in each module `pom.xml` (not recommended for this assignment stack).

- Build one service:
  - `mvn -f services/user-service/pom.xml -DskipTests package`
- Run one service:
  - `mvn -f services/user-service/pom.xml spring-boot:run`
- Run unit tests for one service:
  - `mvn -f services/user-service/pom.xml test`
- Run a single test class:
  - `mvn -f services/user-service/pom.xml -Dtest=AuthServiceTest test`
- Run a single test method:
  - `mvn -f services/user-service/pom.xml -Dtest=AuthServiceTest#login_ok test`

### Repo helper scripts

- Build everything:
  - Windows: `scripts/build-all.cmd`
  - Linux: `bash scripts/build-all.sh`
- Run everything locally (separate processes):
  - Windows: `scripts/run-local.cmd`
  - Linux: `bash scripts/run-local.sh`

### Useful env vars

- `CONFIG_SERVER_URL` (default `http://localhost:8888`)
- `EUREKA_URL` (default `http://localhost:8761/eureka`)
- `CONFIG_REPO_PATH` (Config Server native repo path)
- `USER_DB_URL`, `USER_DB_USERNAME`, `USER_DB_PASSWORD`
- `PRODUCT_MONGO_URI`, `ORDER_MONGO_URI`
- `STORAGE_PROVIDER` (`local|gcs`), `LOCAL_STORAGE_DIR`, `GCS_PROJECT_ID`, `GCS_BUCKET`, `GCS_CREDENTIALS_PATH`

### Platform start order

1. Config Server (`platform/config-server`) on `:8888`
2. Eureka (`platform/eureka-server`) on `:8761`
3. API Gateway (`platform/api-gateway`) on `:8080`
4. Microservices (`services/*`) on `:8081-8083`

### Frontend

- Static HTML/JS under `frontend/`.
- No build step; open `frontend/index.html` and point base URL to the gateway.

### PM2 (VM deployments)

- Start all services on a VM:
  - `pm2 start pm2/ecosystem.config.cjs`
- View processes:
  - `pm2 monit`
- Persist on reboot:
  - `pm2 save`
  - `pm2 startup` (then run the printed command)

### GCP VM verification (screen recording)

- SSH into each VM and run:
  - `pm2 monit`
- Record the running processes + logs to prove auto-restart.

## Configuration (Config Server)

- Config files live in `config-repo/` and are read by Config Server.
- Service files: `user-service.yml`, `product-service.yml`, `order-service.yml`, etc.
- All placeholders for GCP credentials, project id, bucket, and database URLs must be replaced for your environment.

## Code Style Guidelines

### General

- Keep services independent; do not add cross-service compile-time dependencies.
- Prefer explicit, boring code over clever abstractions (this is a student project).
- Add/modify endpoints via controllers + DTOs; keep entities/documents persistence-focused.

### Imports

- No wildcard imports.
- Import ordering: Java stdlib, then `org.*`, then `com.*`.
- Prefer constructor injection; avoid field injection.

### Formatting

- 4-space indentation.
- Keep lines <= 120 chars when reasonable.
- Prefer small methods; avoid deep nesting.

### Types and DTOs

- Use DTOs for request/response; do not expose JPA entities/Mongo documents directly.
- Validate incoming payloads with `jakarta.validation` annotations (`@NotBlank`, `@Email`, etc.).
- Keep IDs as `String` for Mongo documents and `Long` for JPA entities.

### Naming

- Packages: lowercase, dot-separated, e.g. `com.ijse.eca.users`.
- Classes: `PascalCase`; methods/fields: `camelCase`.
- REST endpoints: plural nouns, resource-oriented (`/products`, `/orders`).
- Constants: `UPPER_SNAKE_CASE`.

### Error Handling

- Use a global `@RestControllerAdvice` per service.
- Return consistent error shapes:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/..." }
```

- Throw typed exceptions for domain errors (e.g., `NotFoundException`, `ConflictException`).
- Never leak secrets/credentials in error messages or logs.

### Logging

- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`).
- Log at `info` for lifecycle events; `warn` for expected failures; `error` for unexpected exceptions.
- Do not log passwords, JWT secrets, credential JSON, or full tokens.

### Security

- `user-service` issues JWTs; keep auth endpoints under `/auth/*`.
- Do not hardcode secrets; read from Config Server / environment.

### Storage (Product images)

- `storage.provider=local` is for dev; `storage.provider=gcs` uses Google Cloud Storage.
- Do not commit real service account keys; keep only placeholder paths in config.

## Cursor/Copilot Rules

- No `.cursor/rules`, `.cursorrules`, or `.github/copilot-instructions.md` were detected in this workspace at generation time.
