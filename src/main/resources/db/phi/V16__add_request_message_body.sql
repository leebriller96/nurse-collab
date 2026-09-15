-- =========================================================
-- V16__add_request_message_body.sql  (원내 DB — db/phi)
--
-- 요청에 붙는 대화의 **내용**을 원내에 둔다.
-- "환자분 열이 38.5도라 검사 미뤄주세요" 같은 말이 섞인다. 이건 진료정보다.
-- 업무 쪽 request_message 에는 누가·언제와 이 테이블을 가리키는 message_ref 만 남는다(V17).
--
-- 업무 쪽 테이블을 외래키로 잇지 않는다. 두 DB 로 갈라 두어야 하기 때문이다.
-- 보낸 사람 이름은 쓸 때 굳힌다. 기록에 찍힌 이름은 그때 그 사람이어야 한다.
-- =========================================================

CREATE TABLE request_message_body (
    message_ref     UUID          PRIMARY KEY,
    order_id        BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    sender_name     VARCHAR(50)   NOT NULL,
    content         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rmb_order ON request_message_body (order_id, created_at);

COMMENT ON TABLE request_message_body IS
'요청에 붙는 대화의 내용. 업무 쪽 request_message.message_ref 가 이 행을 가리킨다';
