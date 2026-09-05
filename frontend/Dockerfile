# ── 빌드 단계 ────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /build

# 잠금 파일 그대로 설치한다. 배포본과 로컬이 다른 버전으로 갈리면 안 된다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ── 실행 단계 ────────────────────────────────────────────────
# Caddy 를 쓰는 이유는 인증서 발급과 갱신을 알아서 하기 때문이다.
# 갱신이 조용히 실패하는 것이 이 구성에서 제일 무서운 고장이다.
FROM caddy:2-alpine
ENV TZ=Asia/Seoul

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv

EXPOSE 80 443
