# ── 빌드 단계 ────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /build

# 잠금 파일 그대로 설치한다. 배포본과 로컬이 다른 버전으로 갈리면 안 된다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ── 실행 단계 ────────────────────────────────────────────────
FROM nginx:1.27-alpine
ENV TZ=Asia/Seoul

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
