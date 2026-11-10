# syntax=docker/dockerfile:1.7

# ---- Build the static SPA ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
# Monaco + Mermaid bundling needs more than Node's default heap on small Docker VMs.
ENV NODE_OPTIONS=--max-old-space-size=3072
RUN npm run build

# ---- Serve with nginx ----
FROM nginx:1.28-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
