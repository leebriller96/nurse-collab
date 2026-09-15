-- =========================================================
-- V12__add_phi_access_log.sql
-- 원내가 스스로 남기는 접근 기록.
--
-- 클라우드의 audit_log 와 별개다. 왜 따로 두는가:
--
--   1. 클라우드가 뚫렸을 때 무엇이 나갔는지는 클라우드 로그로 알 수 없다.
--      로그도 같이 지워지거나 고쳐질 수 있기 때문이다.
--      진료정보가 실제로 나간 곳은 원내이므로, 그 기록도 원내에 있어야 한다.
--   2. **거절된 시도도 남긴다.** 클라우드의 감사 AOP 는 성공한 요청만 적는다.
--      그런데 조사할 때 제일 보고 싶은 것은 "누가 볼 수 없는 것을 열려고 했는가" 다.
--
-- 설계 근거는 docs/06-hospital-scale.md 5장.
-- =========================================================

CREATE TABLE phi_access_log (
    id              BIGSERIAL    PRIMARY KEY,

    -- 직원 정보는 업무 쪽(staff)에 있다. 외래키를 걸지 않는다.
    -- 이 로그는 클라우드가 사라져도 그것만으로 읽을 수 있어야 하므로
    -- 아이디와 소속을 문자열로 함께 적어 둔다.
    actor_id        BIGINT       NOT NULL,
    actor_login_id  VARCHAR(50)  NOT NULL,
    actor_dept_id   BIGINT,

    subject_ref     UUID         NOT NULL,
    patient_id      BIGINT,                         -- 거절된 시도에서는 비어 있을 수 있다

    action          VARCHAR(20)  NOT NULL,          -- VIEW / CHECKLIST / SEARCH
    granted         BOOLEAN      NOT NULL,          -- 거절된 시도도 남긴다
    denied_reason   VARCHAR(100),                   -- NOT_RELATED / RATE_LIMITED

    ip_address      INET,
    user_agent      VARCHAR(300),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 조회량 제한이 "최근 N분 동안 이 사람이 연 서로 다른 환자 수" 를 센다.
-- 그 쿼리가 매 조회마다 돌므로 인덱스가 없으면 열수록 느려진다.
CREATE INDEX idx_phi_actor_time ON phi_access_log(actor_id, occurred_at DESC);

-- 조사할 때는 "이 환자를 누가 열었나" 로 본다.
CREATE INDEX idx_phi_subject_time ON phi_access_log(subject_ref, occurred_at DESC);

-- 거절된 시도만 따로 보는 일이 잦다. 전체에 비해 아주 적어서 부분 인덱스가 맞다.
CREATE INDEX idx_phi_denied ON phi_access_log(occurred_at DESC) WHERE granted = FALSE;

COMMENT ON TABLE phi_access_log IS
'원내 자체 접근 기록. 거절된 시도까지 남긴다. 지우거나 고치지 않는다';
