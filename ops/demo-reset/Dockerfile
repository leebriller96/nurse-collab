# 공개 데모의 데이터를 매일 처음 상태로 되돌린다.
#
#   docker build -f ops/demo-reset/Dockerfile ops/demo-reset
FROM node:22-alpine

ENV TZ=Asia/Seoul

# 데이터를 비우고 시드를 다시 넣는 데 psql 이 필요하다.
# 도커 소켓을 주지 않기 위해 이미지 안에 직접 넣는다.
RUN apk add --no-cache postgresql16-client tzdata

WORKDIR /app
COPY reset.mjs ./
# 다시 심을 시드. reset.mjs 가 이름 순서대로 전부 실행하므로
# 파일을 더할 때 이 줄이나 스크립트를 고칠 일이 없다.
COPY seed/ seed/

CMD ["node", "reset.mjs"]
