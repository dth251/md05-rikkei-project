#!/usr/bin/env bash
set -euo pipefail

psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 \
    --set auth_password="$AUTH_DB_PASSWORD" \
    --set product_password="$PRODUCT_DB_PASSWORD" \
    --set order_password="$ORDER_DB_PASSWORD" \
    --set payment_password="$PAYMENT_DB_PASSWORD" \
    --set notification_password="$NOTIFICATION_DB_PASSWORD" <<'SQL'
CREATE USER auth_user WITH PASSWORD :'auth_password';
CREATE USER product_user WITH PASSWORD :'product_password';
CREATE USER order_user WITH PASSWORD :'order_password';
CREATE USER payment_user WITH PASSWORD :'payment_password';
CREATE USER notification_user WITH PASSWORD :'notification_password';

CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE product_db OWNER product_user;
CREATE DATABASE order_db OWNER order_user;
CREATE DATABASE payment_db OWNER payment_user;
CREATE DATABASE notification_db OWNER notification_user;

REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
REVOKE ALL ON DATABASE product_db FROM PUBLIC;
REVOKE ALL ON DATABASE order_db FROM PUBLIC;
REVOKE ALL ON DATABASE payment_db FROM PUBLIC;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
SQL
