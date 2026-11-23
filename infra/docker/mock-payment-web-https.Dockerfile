FROM node:20-alpine AS build

ARG VITE_ORDER_SERVICE_URL=
ENV VITE_ORDER_SERVICE_URL=$VITE_ORDER_SERVICE_URL

WORKDIR /app
COPY frontend/mock-payment-web/package*.json ./
RUN npm ci
COPY frontend/mock-payment-web .
RUN npm run build

FROM nginx:1.27-alpine
COPY infra/docker/nginx/mock-payment-web-https.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3443
