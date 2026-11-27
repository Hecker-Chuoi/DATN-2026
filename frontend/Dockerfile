# Stage 1: Build React application (Vite 8 cần Node ^20.19 hoặc >=22.12)
FROM node:22-alpine AS builder
WORKDIR /app

# Cài đúng phiên bản trong package-lock.json (lỗi thì fail, không âm thầm npm install)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build
COPY . .

# Vite NHÚNG biến VITE_* vào bundle lúc build (không đổi được khi chạy) nên phải truyền bằng --build-arg:
#   docker build --build-arg VITE_KEYCLOAK_URL=https://sso.<domain> -t datn-frontend frontend/
# Không truyền thì để rỗng -> App.jsx dùng mặc định http://localhost:8088 (như trước đây).
ARG VITE_KEYCLOAK_URL=""
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
RUN npm run build

# Stage 2: Serve using Nginx
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
