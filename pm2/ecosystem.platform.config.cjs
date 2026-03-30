/*
  PM2: platform tier only — Config Server, Eureka, API Gateway.
  Use on "platform" MIG VMs (instance template with metadata eca-pm2-ecosystem=platform).

  Before pm2 start:
    source deploy/gcp-vm.env
  Platform VMs usually keep CONFIG_SERVER_URL on loopback; EUREKA_URL should be reachable by
  *service* VMs too (same internal ILB hostname or platform subnet IP).
*/

const CONFIG_SERVER_URL = process.env.CONFIG_SERVER_URL || 'http://127.0.0.1:8888'
const EUREKA_URL = process.env.EUREKA_URL || 'http://127.0.0.1:8761/eureka'
const CONFIG_REPO_PATH = process.env.CONFIG_REPO_PATH || '../../config-repo'

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
      name: 'config-server',
      cwd: 'platform/config-server',
      script: 'java',
      args: ['-jar', 'target/config-server-0.1.0-SNAPSHOT.jar'],
      autorestart: true,
      max_restarts: 20,
      time: true,
      out_file: '../../logs/config-server.out.log',
      error_file: '../../logs/config-server.err.log',
      env: { CONFIG_REPO_PATH }
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
      env: { ...commonServiceEnv }
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
      env: { ...commonServiceEnv }
    }
  ]
}
