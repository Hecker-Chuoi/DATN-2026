# =============================================================================
# frontend/Dockerfile – bản đề xuất (thay cho bản hiện tại)
# Thay đổi so với bản cũ:
#   1. node:20 → node:22 (Node 20 đã hết hạn hỗ trợ 04/2026; Vite 8 cần ^20.19 | >=22.12)
#   2. Nhận VITE_KEYCLOAK_URL qua --build-arg: biến VITE_* được "đóng băng" vào bundle
#      JS lúc `npm run build`, KHÔNG đổi được bằng biến môi trường lúc container chạy.
#   3. Chọn file nginx bằng --build-arg NGINX_CONF (mặc định nginx.conf như cũ để
#      docker-compose local của nhóm không bị ảnh hưởng; production dùng nginx.prod.conf).
#   4. Bỏ "|| npm install": CI phải cài đúng theo package-lock.json, lệch thì fail sớm.
# .dockerignore hiện có đã loại node_modules, dist → không bị lẫn node_modules của máy build.
# =============================================================================

# ---------- Stage 1: build bundle React ----------
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
ARG VITE_KEYCLOAK_URL=http://localhost:8088
ENV VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}
RUN npm run build

# ---------- Stage 2: phục vụ file tĩnh bằng Nginx ----------
FROM nginx:stable-alpine
ARG NGINX_CONF=nginx.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
