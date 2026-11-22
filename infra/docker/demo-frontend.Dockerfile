FROM node:22-alpine
WORKDIR /app
ARG APP
COPY frontend/${APP}/package*.json ./
RUN npm ci
COPY frontend/${APP}/ ./
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--strictPort"]
