/*
  PM2: business microservices only — user, product, order.
  Use on "services" MIG VMs (metadata eca-pm2-ecosystem=services).

  Required env (set in deploy/gcp-vm.env):
    CONFIG_SERVER_URL — http://<PLATFORM_INTERNAL_LB_OR_IP>:8888
    EUREKA_URL        — http://<PLATFORM_INTERNAL_LB_OR_IP>:8761/eureka
  Never use localhost for those on service VMs unless Config & Eureka run on the same instance.
*/

const CONFIG_SERVER_URL = process.env.CONFIG_SERVER_URL || 'http://127.0.0.1:8888'
const EUREKA_URL = process.env.EUREKA_URL || 'http://127.0.0.1:8761/eureka'
const STORAGE_PROVIDER = process.env.STORAGE_PROVIDER || 'gcs'

const commonServiceEnv = {
  CONFIG_SERVER_URL,
  EUREKA_URL,
  ...(process.env.SPRING_PROFILES_ACTIVE
    ? { SPRING_PROFILES_ACTIVE: process.env.SPRING_PROFILES_ACTIVE }
    : {})
}

module.exports = {
  apps: [
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
      env: { ...commonServiceEnv }
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
      env: { ...commonServiceEnv }
    }
  ]
}
