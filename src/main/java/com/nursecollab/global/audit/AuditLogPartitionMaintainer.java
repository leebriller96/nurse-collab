package com.nursecollab.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 감사 로그의 다음 달 파티션을 미리 만들어 둔다.
 *
 * 파티션이 없으면 행은 기본 파티션으로 들어간다. INSERT 는 성공하고 화면도 멀쩡하다.
 * 그래서 파티션을 나눈 의미가 사라진 채로 몇 년이 지나갈 수 있다.
 * 손으로 만들어 둔 것은 2027년 3월에서 끝난다.
 *
 * DDL 은 Flyway 가 만든 함수 안에 있다. 여기서는 부르기만 한다.
 * 스키마의 모양을 아는 곳이 두 군데가 되면 안 되기 때문이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogPartitionMaintainer {

    /**
     * 몇 달 앞까지 미리 만들어 둘지.
     * 하루에 한 번 도는데 석 달치를 미리 두는 것은, 이 작업이 며칠 밀려도
     * 기본 파티션으로 새어 나가지 않게 하려는 것이다.
     */
    private static final int MONTHS_AHEAD = 3;

    private final JdbcTemplate jdbcTemplate;

    /** 기동할 때 한 번. 오래 꺼져 있던 인스턴스가 올라오는 경우를 받는다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ensurePartitions();
    }

    /** 데모 초기화(04:00)보다 먼저 돌게 둔다 */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void daily() {
        ensurePartitions();
    }

    private void ensurePartitions() {
        try {
            Integer created = jdbcTemplate.queryForObject(
                    "select ensure_audit_log_partitions(?)", Integer.class, MONTHS_AHEAD);
            if (created != null && created > 0) {
                log.info("감사 로그 파티션 {}개를 새로 만들었다", created);
            }

            Long stray = jdbcTemplate.queryForObject(
                    "select count(*) from audit_log_default", Long.class);
            if (stray != null && stray > 0) {
                // 여기 행이 있다는 것은 이 작업이 이미 한 번 밀렸다는 뜻이다.
                // 그 달 파티션을 만들려면 이 행들을 먼저 옮겨야 한다.
                log.warn("기본 파티션에 감사 로그 {}건이 쌓여 있다. 파티션 생성이 밀렸다는 뜻이다", stray);
            }
        } catch (Exception e) {
            // 실패해도 기동을 막지 않는다. 기본 파티션이 받아 주므로 기록 자체는 계속 남는다.
            // 감사 때문에 진료가 멈추면 안 된다는 원칙과 같다.
            log.error("감사 로그 파티션 정리에 실패했다", e);
        }
    }
}
