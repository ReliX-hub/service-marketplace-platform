FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=/api
ARG VITE_ENABLE_DEMO_FALLBACK=true
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ENABLE_DEMO_FALLBACK=$VITE_ENABLE_DEMO_FALLBACK
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -qO- http://localhost/healthz >/dev/null || exit 1
