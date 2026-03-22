@echo off
setlocal

REM Builds all services (requires JDK 25)

call mvn -f platform\config-server\pom.xml -DskipTests package || exit /b 1
call mvn -f platform\eureka-server\pom.xml -DskipTests package || exit /b 1
call mvn -f platform\api-gateway\pom.xml -DskipTests package || exit /b 1
call mvn -f services\user-service\pom.xml -DskipTests package || exit /b 1
call mvn -f services\product-service\pom.xml -DskipTests package || exit /b 1
call mvn -f services\order-service\pom.xml -DskipTests package || exit /b 1

echo.
echo Build OK
