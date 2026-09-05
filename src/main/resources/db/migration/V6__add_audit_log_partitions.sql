-- audit_log 는 occurred_at 으로 월 단위 파티션된다.
-- V1 은 2026년 9월 파티션 하나만 만들어 뒀다. 다음 달이 되는 순간 INSERT 가 실패한다.
-- 감사 기록이 안 남는 것은 조용히 지나갈 문제가 아니므로 미리 만들어 둔다.
CREATE TABLE audit_log_202610 PARTITION OF audit_log FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_log_202611 PARTITION OF audit_log FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE audit_log_202612 PARTITION OF audit_log FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE audit_log_202701 PARTITION OF audit_log FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE audit_log_202702 PARTITION OF audit_log FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE audit_log_202703 PARTITION OF audit_log FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');

-- 그래도 빠뜨릴 수 있으니 받아줄 곳을 둔다.
-- 여기 쌓인 행이 있으면 나중에 그 달 파티션을 만들 때 옮겨야 하므로,
-- 운영에서는 파티션을 미리 만드는 작업을 걸어 두고 이 테이블은 비어 있어야 정상이다.
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

COMMENT ON TABLE audit_log_default IS
'월 파티션이 없을 때 받아주는 곳. 비어 있는 것이 정상이며, 행이 쌓이면 파티션 생성이 밀린 것이다';
