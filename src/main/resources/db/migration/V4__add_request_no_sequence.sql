-- 요청번호(TR20260904-0001)의 일자별 일련번호.
--
-- "오늘 만들어진 요청 수 + 1" 로 계산하면 두 병동이 동시에 등록할 때 같은 번호가 나온다.
-- 이 프로젝트가 동시성 처리를 보여주는 것이 목적인데 발번부터 경합에 깨지면 앞뒤가 안 맞는다.
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING 으로 DB 가 원자적으로 발번하게 한다.
CREATE TABLE request_no_sequence (
    date_key DATE PRIMARY KEY,
    last_no  INT  NOT NULL
);

COMMENT ON TABLE request_no_sequence IS
'요청번호 일자별 일련번호. 트랜잭션 안에서 원자적으로 증가시킨다';
