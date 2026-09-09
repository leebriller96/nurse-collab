package com.nursecollab.global.audit;

import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 로그 파티션이 저절로 채워지는지 확인한다.
 *
 * 이 기능은 고장나도 아무 증상이 없다. 파티션이 없으면 행이 기본 파티션으로
 * 들어가고 INSERT 는 성공하기 때문이다. 그래서 테스트가 아니면 알 길이 없다.
 */
class AuditLogPartitionMaintainerTest extends IntegrationTest {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired private AuditLogPartitionMaintainer maintainer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 이번_달부터_석_달_뒤까지_파티션이_있다() {
        // 기동할 때 이미 한 번 돌았다
        LocalDate month = LocalDate.now().withDayOfMonth(1);

        for (int i = 0; i <= 3; i++) {
            String name = "audit_log_" + month.plusMonths(i).format(YYYYMM);
            assertThat(exists(name))
                    .as("%s 파티션이 있어야 한다", name)
                    .isTrue();
        }
    }

    @Test
    void 여러_번_불러도_안전하다() {
        // 두 인스턴스가 동시에 올라오거나 하루에 여러 번 도는 상황.
        // 기동할 때 이미 만들었으므로 더 만들 것이 없어야 한다.
        assertThat(callFunction()).isZero();
        assertThat(callFunction()).isZero();
    }

    @Test
    void 기본_파티션은_비어_있다() {
        // 여기 행이 쌓였다면 파티션 생성이 밀렸다는 뜻이다.
        // 테스트가 도는 동안 감사 로그가 여럿 쌓이는데, 전부 제 달 파티션으로 가야 한다.
        Long stray = jdbcTemplate.queryForObject(
                "select count(*) from audit_log_default", Long.class);

        assertThat(stray).isZero();
    }

    @Test
    void 빠진_달이_있으면_채운다() {
        // 다음 달로 넘어가 새 달이 필요해진 상황을 만든다
        String name = "audit_log_" + LocalDate.now().withDayOfMonth(1).plusMonths(3).format(YYYYMM);
        jdbcTemplate.execute("drop table if exists " + name);
        assertThat(exists(name)).isFalse();

        maintainer.daily();

        assertThat(exists(name))
                .as("하루 한 번 도는 작업이 빠진 달을 채워야 한다")
                .isTrue();
    }

    @Test
    void 손으로_만들어_둔_마지막_달_너머도_만든다() {
        // 이 기능의 존재 이유다. V1 과 V6 이 손으로 만들어 둔 것은 2027년 3월에서 끝난다.
        // 그 뒤로는 전부 기본 파티션으로 들어가는데 INSERT 는 성공하므로 아무도 모른다.
        LocalDate far = LocalDate.now().withDayOfMonth(1).plusMonths(12);
        String name = "audit_log_" + far.format(YYYYMM);
        jdbcTemplate.execute("drop table if exists " + name);

        jdbcTemplate.queryForObject("select ensure_audit_log_partitions(12)", Integer.class);

        assertThat(exists(name))
                .as("%s 까지 요청하면 그 달 파티션이 있어야 한다", far)
                .isTrue();
    }

    /** 함수는 새로 만든 개수를 돌려준다 */
    private int callFunction() {
        Integer created = jdbcTemplate.queryForObject(
                "select ensure_audit_log_partitions(3)", Integer.class);
        return created == null ? 0 : created;
    }

    private boolean exists(String tableName) {
        Boolean found = jdbcTemplate.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, tableName);
        return Boolean.TRUE.equals(found);
    }
}
