# Stage 1: Build Angular static assets
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# Stage 2: Air-Gapped Nginx Web Server. The unprivileged image listens on 8080 and runs under any UID, as
# OpenShift's restricted SCC requires; mirror it into your registry for air-gapped builds.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
# Rendered into /etc/nginx/conf.d/default.conf at start, with PORTAL_API_URL from the environment
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
ENV PORTAL_API_URL=http://core-service:8080

EXPOSE 8080
