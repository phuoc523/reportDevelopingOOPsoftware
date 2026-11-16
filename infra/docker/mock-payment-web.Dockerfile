FROM node:20-alpine

WORKDIR /app

COPY frontend/mock-payment-web/package*.json ./
RUN npm install

COPY frontend/mock-payment-web .

EXPOSE 3000

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3000"]
