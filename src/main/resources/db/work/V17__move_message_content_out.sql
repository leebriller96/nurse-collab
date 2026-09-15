-- =========================================================
-- V17__move_message_content_out.sql  (업무 DB — db/work)
--
-- 대화 내용을 업무 쪽에서 걷어낸다. 남는 것은 누가·언제와 원내 본문을 가리키는 열쇠다.
-- 그래야 원내망 밖에서도 "대화가 3건 있는데 여기서는 못 읽는다" 를 말할 수 있다.
-- =========================================================

ALTER TABLE request_message ADD COLUMN message_ref UUID;
UPDATE request_message SET message_ref = gen_random_uuid() WHERE message_ref IS NULL;

DO $$
BEGIN
    IF to_regclass('public.request_message_body') IS NOT NULL THEN
        -- 합친 DB. 원내 본문 테이블이 같은 DB 에 있으니 내용을 옮기고 지운다.
        INSERT INTO request_message_body (message_ref, order_id, sender_id, sender_name, content, created_at)
        SELECT m.message_ref, m.order_id, m.sender_id, s.name, m.content, m.created_at
          FROM request_message m
          JOIN staff s ON s.id = m.sender_id;
    ELSIF EXISTS (SELECT 1 FROM request_message) THEN
        -- 업무 서버 DB 인데 대화가 이미 쌓여 있다. 옮길 곳이 이 DB 에 없다.
        -- 그대로 칸을 지우면 대화 내용이 조용히 사라진다. 멈춘다.
        RAISE EXCEPTION
            '업무 DB 에 대화 내용이 남아 있는데 옮길 원내 테이블이 이 DB 에 없습니다. '
            'request_message 의 내용을 원내 DB 의 request_message_body 로 먼저 옮기세요.';
    END IF;
END $$;

ALTER TABLE request_message ALTER COLUMN message_ref SET NOT NULL;
ALTER TABLE request_message ADD CONSTRAINT uq_request_message_ref UNIQUE (message_ref);
ALTER TABLE request_message DROP COLUMN content;

COMMENT ON TABLE request_message IS
'요청에 붙는 대화의 누가·언제. 내용은 원내 request_message_body 에 있다';
