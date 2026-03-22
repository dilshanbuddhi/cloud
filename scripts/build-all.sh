#!/usr/bin/env bash
set -euo pipefail

# Builds all services (requires JDK 25)

mvn -f platform/config-server/pom.xml -DskipTests package
mvn -f platform/eureka-server/pom.xml -DskipTests package
mvn -f platform/api-gateway/pom.xml -DskipTests package
mvn -f services/user-service/pom.xml -DskipTests package
mvn -f services/product-service/pom.xml -DskipTests package
mvn -f services/order-service/pom.xml -DskipTests package

echo "Build OK"
