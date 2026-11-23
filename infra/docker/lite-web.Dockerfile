FROM node:22-alpine AS build
WORKDIR /src
COPY frontend/admin-web ./admin-web
COPY frontend/kiosk-web ./kiosk-web
COPY frontend/mock-payment-web ./mock-payment-web
RUN cd admin-web && npm ci --no-audit && npm run build
ARG VITE_KIOSK_MACHINE_ID=9101
RUN cd kiosk-web && npm ci --no-audit && VITE_KIOSK_MACHINE_ID=$VITE_KIOSK_MACHINE_ID npm run build
RUN cd mock-payment-web && npm ci --no-audit && VITE_LITE_PROXY=1 npm run build

FROM nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /src/admin-web/dist /var/www/admin
COPY --from=build /src/kiosk-web/dist /var/www/kiosk
COPY --from=build /src/mock-payment-web/dist /var/www/mock
COPY infra/docker/lite-web.conf.template /etc/nginx/templates/lite.conf.template
