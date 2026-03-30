/*
  PM2 ecosystem file for non-containerized VM deployments (e.g. GCP GCE).

  Usage:
    1) Build all jars: mvn -f <service>/pom.xml -DskipTests package
    2) Copy repo to VM (or git clone)
    3) Optional: source deploy/gcp-vm.env (see docs/GCP-VM-DEPLOYMENT.md)
    4) pm2 start pm2/ecosystem.config.cjs

  Environment variables (export before pm2 start, or set in shell profile):
    CONFIG_SERVER_URL=http://127.0.0.1:8888
    EUREKA_URL=http://127.0.0.1:8761/eureka
    # Production (GCP): set EUREKA_URL to http://<public-or-internal-eureka-host>:8761/eureka — not localhost.
    CONFIG_REPO_PATH=../../config-repo   (or absolute path on VM)
    STORAGE_PROVIDER=gcs                  (default below; was wrongly forced to local)
    GOOGLE_APPLICATION_CREDENTIALS=...   (if not using GCE metadata SA)

  VM public IP example: 35.194.27.132 — users open http://<IP>:8080/

  Notes:
    - JDK 25 required (match pom.xml). Set JAVA_HOME if `java` is not 25+.
*/

const CONFIG_SERVER_URL = process.env.CONFIG_SERVER_URL || 'http://127.0.0.1:8888'
// Set EUREKA_URL before pm2 (e.g. source deploy/gcp-vm.env). GCP example: http://34.55.203.126:8761/eureka
const EUREKA_URL = process.env.EUREKA_URL || 'http://127.0.0.1:8761/eureka'
const CONFIG_REPO_PATH = process.env.CONFIG_REPO_PATH || '../../config-repo'
const STORAGE_PROVIDER = process.env.STORAGE_PROVIDER || 'gcs'

const commonServiceEnv = {
  CONFIG_SERVER_URL,
  EUREKA_URL
}

module.exports = {
  apps: [
    {
      name: 'config-server',
      cwd: 'platform/config-server',
      script: 'java',
      args: ['-jar', 'target/config-server-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/config-server.out.log',
      error_file: '../../logs/config-server.err.log',
      env: {
        CONFIG_REPO_PATH
      }
    },
    {
      name: 'eureka-server',
      cwd: 'platform/eureka-server',
      script: 'java',
      args: ['-jar', 'target/eureka-server-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/eureka-server.out.log',
      error_file: '../../logs/eureka-server.err.log',
      env: {
        ...commonServiceEnv
      }
    },
    {
      name: 'api-gateway',
      cwd: 'platform/api-gateway',
      script: 'java',
      args: ['-jar', 'target/api-gateway-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/api-gateway.out.log',
      error_file: '../../logs/api-gateway.err.log',
      env: {
        ...commonServiceEnv
      }
    },
    {
      name: 'user-service',
      cwd: 'services/user-service',
      script: 'java',
      args: ['-jar', 'target/user-service-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/user-service.out.log',
      error_file: '../../logs/user-service.err.log',
      env: {
        ...commonServiceEnv
      }
    },
    {
      name: 'product-service',
      cwd: 'services/product-service',
      script: 'java',
      args: ['-jar', 'target/product-service-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/product-service.out.log',
      error_file: '../../logs/product-service.err.log',
      env: {
        ...commonServiceEnv,
        STORAGE_PROVIDER,
        LOCAL_STORAGE_DIR: process.env.LOCAL_STORAGE_DIR || './data/uploads'
      }
    },
    {
      name: 'order-service',
      cwd: 'services/order-service',
      script: 'java',
      args: ['-jar', 'target/order-service-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/order-service.out.log',
      error_file: '../../logs/order-service.err.log',
      env: {
        ...commonServiceEnv
      }
    }
  ]
}
