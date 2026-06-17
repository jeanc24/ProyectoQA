# ========== STAGE 1: BUILD ==========
FROM node:22-alpine AS build

WORKDIR /app

# Copy only necessary files to install dependencies (use Docker cache)
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the code
COPY . .

# Variables that Vite needs TO BUILD (not after)
ARG VITE_API_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8081
ARG VITE_KEYCLOAK_REALM=inventory
ARG VITE_KEYCLOAK_CLIENT_ID=inventory-frontend

ENV VITE_API_URL=$VITE_API_URL
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npm run build

# ========== STAGE 2: SERVE ==========
# nginx is a web server that will serve the compiled files to make 
# them accessible to the browser (index.html, etc.)
FROM nginx:alpine

# Copy the compiled files to the nginx directory
COPY --from=build /app/dist /usr/share/nginx/html

# Copy your configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80