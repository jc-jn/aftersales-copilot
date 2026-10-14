FROM node:22.14.0-alpine AS build
WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
COPY contracts/ /build/contracts/
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

FROM nginx:1.27.4-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/web/dist /usr/share/nginx/html
EXPOSE 80
