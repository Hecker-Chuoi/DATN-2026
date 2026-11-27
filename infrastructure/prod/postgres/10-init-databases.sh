#!/bin/sh
# MỘT instance Postgres chứa hai database: datn_main_db (identity, work, workspace, notification) và keycloak,
# mỗi database một user riêng (user ứng dụng KHÔNG phải superuser). Mật khẩu lấy từ biến môi trường (secrets.env).
#
# Image postgres chạy mọi *.sh trong /docker-entrypoint-initdb.d MỘT LẦN khi thư mục dữ liệu còn TRỐNG.
# Đổi mật khẩu về sau phải ALTER USER (docs/cicd/cac_buoc/03). Trong lúc chạy, server tạm CHỈ nghe unix socket;
# cổng TCP chỉ mở sau khi script xong => healthcheck phải hỏi qua TCP (pg_isready -h 127.0.0.1).
set -eu

# Biến psql (:"ident" / :'literal') tự quote đúng, không phải nối chuỗi SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v app_user="$APP_DB_USER" -v app_pass="$APP_DB_PASSWORD" -v app_db="$APP_DB_NAME" \
  -v kc_user="$KC_DB_USER"   -v kc_pass="$KC_DB_PASSWORD"   -v kc_db="$KC_DB_NAME" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_pass';
CREATE ROLE :"kc_user"  LOGIN PASSWORD :'kc_pass';
CREATE DATABASE :"app_db" OWNER :"app_user";
CREATE DATABASE :"kc_db"  OWNER :"kc_user";
-- Mỗi user chỉ vào được database của mình
REVOKE CONNECT ON DATABASE :"app_db" FROM PUBLIC;
REVOKE CONNECT ON DATABASE :"kc_db"  FROM PUBLIC;
SQL

# Migration V1 của work-service và workspace-service có CREATE EXTENSION pgcrypto: tạo sẵn bằng superuser
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$APP_DB_NAME" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SQL
