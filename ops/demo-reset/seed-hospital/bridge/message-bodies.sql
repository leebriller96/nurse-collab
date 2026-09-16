-- =========================================================
-- bridge/message-bodies.sql — 요청 메시지 내용 (원내 DB)
--
-- 초기화 스크립트가 업무 DB 에서 읽은 메시지 줄을 message_in 에 넣은 뒤 이 파일을 붙여 돌린다.
-- 시드 디렉터리(phi/)에 두지 않은 것은 업무 DB 를 읽은 다음에만 돌 수 있어서다.
-- 문구는 병동(R)이 보낸 것과 수행 파트(P)가 보낸 것을 나눈다.
-- =========================================================

CREATE TEMP TABLE message_pool (order_type text, side char(1), texts text[]);
INSERT INTO message_pool VALUES
    ('TRANSFER', 'R', ARRAY['환자 수액 달고 내려갑니다', '보호자 동행합니다', '산소 2L 유지 중이라 포터블 산소통 챙겨 주세요',
                            '지금 식사 중인데 30분 뒤에 가능할까요?', '진통제 투여 직후라 조금 늦게 출발합니다',
                            '휠체어 대신 침대로 이송 부탁드립니다', '앞 순서 확인 부탁드려요']),
    ('TRANSFER', 'P', ARRAY['앞 환자 검사가 길어져 20분 정도 밀립니다', '금식 확인 부탁드립니다',
                            '조영제 동의서 챙겨서 보내 주세요', '체내 금속 확인됐나요?', '지금 내려보내 주세요',
                            '검사 끝났습니다. 이송 요원 올라갑니다', '정맥관 20G 이상으로 확보 부탁드립니다']),
    ('SPECIMEN', 'R', ARRAY['채혈 어려워서 조금 늦어집니다', '방금 검체 내려보냈습니다', '결과 나오면 바로 알려 주세요',
                            '수액 반대편에서 채혈했습니다']),
    ('SPECIMEN', 'P', ARRAY['검체 용혈돼서 재채혈 부탁드립니다', '검체량이 부족합니다', '라벨이 안 붙어 왔어요',
                            '결과 올렸습니다. 수치 확인 부탁드려요', '배양은 최종 결과까지 2~3일 걸립니다']),
    ('PHARMACY', 'R', ARRAY['투약 시간이 다가와서 급하게 부탁드려요', '병동 재고 없습니다', '받았습니다, 감사합니다',
                            '용량 변경 처방 새로 났습니다']),
    ('PHARMACY', 'P', ARRAY['처방 용량 확인 중입니다', '대체 약으로 나갑니다. 처방 확인 부탁드려요',
                            '마약류 수령 서명 필요합니다', '기송관으로 보냈습니다', '재고 확인 후 30분 안에 올라갑니다']);

INSERT INTO request_message_body (message_ref, order_id, sender_id, sender_name, content, created_at)
SELECT m.message_ref, m.order_id, m.sender_id, m.sender_name,
       coalesce(pg_temp.pick(p.texts, 'body:' || m.message_ref), '확인 부탁드립니다'),
       m.created_at
FROM message_in m
LEFT JOIN message_pool p ON p.order_type = m.order_type AND p.side = m.side;
