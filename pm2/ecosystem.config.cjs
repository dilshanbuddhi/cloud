/*
  PM2 ecosystem file for non-containerized VM deployments.

  Usage:
    1) Build all jars: mvn -f <service>/pom.xml -DskipTests package
    2) Copy repo to VM (or git clone with submodules if using polyrepo)
    3) pm2 start pm2/ecosystem.config.cjs

  Environment variables (set in the shell or via PM2):
    CONFIG_SERVER_URL=http://<config-server-ip>:8888
    EUREKA_URL=http://<eureka-ip>:8761/eureka
    CONFIG_REPO_PATH=/opt/app/config-repo

  Notes:
    - This assumes each service produces a single runnable jar in target/.
    - Build/run with JDK 25 (match pom.xml). Ensure `java` on PATH is 25+ or set JAVA_HOME in PM2 `env` per app if needed.
*/

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
        CONFIG_REPO_PATH: '../../config-repo'
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
        CONFIG_SERVER_URL: 'http://localhost:8888'
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
        CONFIG_SERVER_URL: 'http://localhost:8888',
        EUREKA_URL: 'http://localhost:8761/eureka'
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
        CONFIG_SERVER_URL: 'http://localhost:8888',
        EUREKA_URL: 'http://localhost:8761/eureka'
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
        CONFIG_SERVER_URL: 'http://localhost:8888',
        EUREKA_URL: 'http://localhost:8761/eureka',
        STORAGE_PROVIDER: 'local',
        LOCAL_STORAGE_DIR: './data/uploads'
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
        CONFIG_SERVER_URL: 'http://localhost:8888',
        EUREKA_URL: 'http://localhost:8761/eureka'
      }
    }
  ]
};
