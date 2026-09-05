# 공개 데모의 데이터를 매일 처음 상태로 되돌린다.
#
# 빌드 컨텍스트는 저장소 루트다. Flyway 시드 파일을 그대로 다시 실행하기 때문에
# 마이그레이션 디렉터리가 필요하다.
#   docker build -f ops/demo-reset/Dockerfile .
FROM node:22-alpine

ENV TZ=Asia/Seoul

# 데이터를 비우고 시드를 다시 넣는 데 psql 이 필요하다.
# 도커 소켓을 주지 않기 위해 이미지 안에 직접 넣는다.
RUN apk add --no-cache postgresql16-client tzdata

WORKDIR /app
COPY ops/demo-reset/reset.mjs ./
# 지킬 목록을 손으로 관리하지 않고 시드 파일을 그대로 다시 돌린다
COPY src/main/resources/db/migration/V3__seed_master_data.sql sql/
COPY src/main/resources/db/migration/V5__seed_demo_patients.sql sql/

CMD ["node", "reset.mjs"]
