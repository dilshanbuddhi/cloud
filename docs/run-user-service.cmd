@echo off
REM Quick start for user-service with Cloud SQL MySQL connection

setlocal enabledelayedexpansion

REM ==========================================
REM Set your GCP Cloud SQL connection details
REM ==========================================

set USER_DB_URL=jdbc:mysql://34.10.103.133:3306/userdb?createDatabaseIfNotExist=true^&useSSL=false^&allowPublicKeyRetrieval=true^&serverTimezone=UTC
set USER_DB_USERNAME=root
set USER_DB_PASSWORD=xp9Qy%%VF[^0Bq][1
set USER_DB_DRIVER=com.mysql.cj.jdbc.Driver

set JWT_SECRET=replace-with-strong-secret-at-least-32-characters
set JWT_ISSUER=eca-mini-ecommerce
set JWT_EXPIRES_MINUTES=240

set CONFIG_SERVER_URL=http://localhost:8888

echo.
echo ==========================================
echo Starting user-service with Cloud SQL MySQL
echo ==========================================
echo Datasource URL: !USER_DB_URL!
echo Datasource User: !USER_DB_USERNAME!
echo ==========================================
echo.

mvn -f services\user-service\pom.xml spring-boot:run

