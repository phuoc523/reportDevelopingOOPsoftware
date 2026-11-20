FROM node:20-alpine

ARG VITE_ORDER_SERVICE_URL
ENV VITE_ORDER_SERVICE_URL=$VITE_ORDER_SERVICE_URL

WORKDIR /app

COPY frontend/mock-payment-web/package*.json ./
RUN npm ci

COPY frontend/mock-payment-web .

EXPOSE 3000

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3000"]
