#!/usr/bin/env bash
set -euo pipefail

# Starts services for local dev (requires DBs + JDK 25)

mvn -f platform/config-server/pom.xml spring-boot:run &
sleep 4
mvn -f platform/eureka-server/pom.xml spring-boot:run &
sleep 4
mvn -f platform/api-gateway/pom.xml spring-boot:run &
sleep 2
mvn -f services/user-service/pom.xml spring-boot:run &
mvn -f services/product-service/pom.xml spring-boot:run &
mvn -f services/order-service/pom.xml spring-boot:run &

wait
