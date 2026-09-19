-- =========================================================
-- V19__add_push_subscription.sql  (업무 DB — db/work)
--
-- 폰 알림을 받을 기기. 알림함에 쌓이는 그 알림을 폰 알림창에도 띄운다.
-- 한 사람이 여러 기기를 쓸 수 있고, 한 기기(endpoint)는 한 사람에게만 붙는다 —
-- 병동 폰은 돌려 쓰므로 다른 사람이 같은 기기로 등록하면 넘겨받는다.
-- =========================================================

CREATE TABLE push_subscription (
    id          BIGSERIAL    PRIMARY KEY,
    staff_id    BIGINT       NOT NULL REFERENCES staff(id),
    endpoint    TEXT         NOT NULL UNIQUE,
    p256dh      VARCHAR(200) NOT NULL,
    auth        VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_sub_staff ON push_subscription (staff_id);
