# ==============================================================================
# Fairish Chat Frontend — Production Multi-Stage Dockerfile
# ==============================================================================

# Stage 1: Build Vite Single-Page Application
FROM node:20-alpine AS builder

WORKDIR /app

# Accept environment build arguments
ARG VITE_API_BASE_URL
ARG VITE_WS_URL

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_WS_URL=${VITE_WS_URL}

# Copy package manifests and install dependencies
COPY package*.json ./
RUN npm ci

# Copy application source code
COPY . .

# Build production assets
RUN npm run build

# Stage 2: Serve optimized assets with Nginx.
# The unprivileged variant runs as UID 101 and listens on 8080 by default, so
# the container never needs root or a privileged port.
FROM nginxinc/nginx-unprivileged:alpine

# Remove default nginx welcome page
USER root
RUN rm -rf /usr/share/nginx/html/*

# Copy build artifacts from builder stage
COPY --from=builder --chown=101:101 /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
COPY --chown=101:101 nginx.conf /etc/nginx/conf.d/default.conf

USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
