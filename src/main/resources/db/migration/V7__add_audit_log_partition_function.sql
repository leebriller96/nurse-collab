-- 감사 로그 월 파티션을 알아서 만들어 주는 함수.
--
-- V1 은 한 달, V6 은 여섯 달을 손으로 만들어 뒀다. 2027년 4월이 되면 거기서 끝난다.
-- 그 뒤로는 전부 기본 파티션으로 들어가는데, INSERT 는 성공하므로 아무도 모른다.
-- 파티션을 나눈 이유가 사라진 채로 몇 년이 갈 수 있다.
--
-- DDL 을 애플리케이션 코드에 두지 않고 여기 함수로 둔 이유:
-- 스키마의 모양을 아는 곳은 Flyway 한 곳이어야 한다. 앱은 부르기만 한다.

CREATE OR REPLACE FUNCTION ensure_audit_log_partitions(months_ahead INT DEFAULT 3)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    start_month DATE;
    part_name   TEXT;
    created     INT := 0;
BEGIN
    -- 이번 달부터 months_ahead 달 뒤까지. 이번 달을 포함해야
    -- 함수를 처음 붙이는 시점에 구멍이 남지 않는다.
    FOR i IN 0..months_ahead LOOP
        start_month := (date_trunc('month', now()) + (i || ' months')::INTERVAL)::DATE;
        part_name   := 'audit_log_' || to_char(start_month, 'YYYYMM');

        IF to_regclass(part_name) IS NULL THEN
            BEGIN
                EXECUTE format(
                    'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
                    part_name,
                    start_month,
                    (start_month + INTERVAL '1 month')::DATE);
                created := created + 1;
            EXCEPTION
                -- 인스턴스 두 대가 동시에 부르면 한쪽이 진다. 이미 있으면 된 것이다.
                WHEN duplicate_table THEN
                    NULL;
                -- 기본 파티션에 그 달 행이 이미 쌓여 있으면 만들 수 없다.
                -- 한 달이 막혔다고 나머지 달까지 못 만들면 더 나빠지므로 넘어간다.
                WHEN OTHERS THEN
                    RAISE WARNING '감사 로그 파티션 % 생성 실패: %', part_name, SQLERRM;
            END;
        END IF;
    END LOOP;

    RETURN created;
END;
$$;

COMMENT ON FUNCTION ensure_audit_log_partitions(INT) IS
'감사 로그 월 파티션을 필요한 만큼 만든다. 여러 번 불러도 안전하다';

-- 붙이는 즉시 한 번 맞춰 둔다
SELECT ensure_audit_log_partitions(3);
