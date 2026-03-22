#!/usr/bin/env bash

# Copy this to your VM and edit values.
# DO NOT commit real secrets/keys.

export CONFIG_REPO_PATH=/opt/app/config-repo
export CONFIG_SERVER_URL=http://127.0.0.1:8888
export EUREKA_URL=http://127.0.0.1:8761/eureka

# Relational DB (Cloud SQL) for user-service
export USER_DB_URL='jdbc:postgresql://<CLOUD_SQL_PRIVATE_IP>:5432/eca_users'
export USER_DB_USERNAME='CHANGE_ME'
export USER_DB_PASSWORD='CHANGE_ME'

# Firestore for product-service and order-service
export FIRESTORE_PROJECT_ID='cloud-module-490703'
export FIRESTORE_PRODUCTS_COLLECTION='products'
export FIRESTORE_ORDERS_COLLECTION='orders'

# Storage (GCS via Application Default Credentials)
export STORAGE_PROVIDER=gcs
export GCS_PROJECT_ID='cloud-module-490703'
export GCS_BUCKET='prod_bucket1234'
# GCS_CREDENTIALS_PATH is not required when using ADC (gcloud auth application-default login)
