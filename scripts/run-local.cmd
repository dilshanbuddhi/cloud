@echo off
setlocal

REM Starts services for local dev (requires DBs + JDK 25)

start "config-server" cmd /c "mvn -f platform\config-server\pom.xml spring-boot:run"
timeout /t 4 >nul

start "eureka-server" cmd /c "mvn -f platform\eureka-server\pom.xml spring-boot:run"
timeout /t 4 >nul

start "api-gateway" cmd /c "mvn -f platform\api-gateway\pom.xml spring-boot:run"
timeout /t 2 >nul

start "user-service" cmd /c "mvn -f services\user-service\pom.xml spring-boot:run"
start "product-service" cmd /c "mvn -f services\product-service\pom.xml spring-boot:run"
start "order-service" cmd /c "mvn -f services\order-service\pom.xml spring-boot:run"

echo.
echo Eureka: http://localhost:8761
echo Gateway: http://localhost:8080
