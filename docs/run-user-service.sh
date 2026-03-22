#!/usr/bin/env bash
# Quick start for user-service with Cloud SQL MySQL connection

set -euo pipefail

# ==========================================
# Set your GCP Cloud SQL connection details
# ==========================================

export USER_DB_URL="jdbc:mysql://34.10.103.133:3306/userdb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export USER_DB_USERNAME="root"
export USER_DB_PASSWORD="xp9Qy%VF[^0Bq][1"
export USER_DB_DRIVER="com.mysql.cj.jdbc.Driver"

export JWT_SECRET="replace-with-strong-secret-at-least-32-characters"
export JWT_ISSUER="eca-mini-ecommerce"
export JWT_EXPIRES_MINUTES="240"

export CONFIG_SERVER_URL="http://localhost:8888"

echo "=========================================="
echo "Starting user-service with Cloud SQL MySQL"
echo "=========================================="
echo "Datasource URL: $USER_DB_URL"
echo "Datasource User: $USER_DB_USERNAME"
echo "==========================================\n"

mvn -f services/user-service/pom.xml spring-boot:run

