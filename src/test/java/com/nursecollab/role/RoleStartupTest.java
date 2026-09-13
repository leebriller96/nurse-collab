package com.nursecollab.role;

import com.nursecollab.NurseCollabApplication;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.episode.controller.WorkRelationController;
import com.nursecollab.domain.episode.service.LocalWorkRelationAdapter;
import com.nursecollab.domain.nursing.service.NursingRecordService;
import com.nursecollab.domain.phi.port.HttpWorkRelationAdapter;
import com.nursecollab.domain.phi.service.SubjectPhiService;
import com.nursecollab.domain.staff.service.AuthService;
import com.nursecollab.domain.staff.service.RefreshTokenStore;
import com.nursecollab.domain.workorder.service.WorkOrderService;
import com.nursecollab.global.audit.AuditLogPartitionMaintainer;
import com.nursecollab.global.config.AppBoundary;
import com.nursecollab.global.security.JwtTokenProvider;
import com.nursecollab.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 한 빌드를 역할별로 <b>실제로</b> 띄워 본다.
 *
 * 역할 필터, 엔티티 목록, 마이그레이션 위치, 가드가 각각 따로 맞아도 함께 띄워 보기 전에는
 * 모른다. 원내에 업무 쪽 저장소 하나가 섞여 올라가면 원내 DB 에 그 테이블이 없어 뜨지 않고,
 * 반대로 업무 DB 에 환자 테이블이 남으면 <b>아무 오류 없이 뜬다</b> — 그게 제일 나쁘다.
 *
 * 테스트용 PostgreSQL 컨테이너 안에 역할별 DB 를 새로 만들어 쓴다.
 */
class RoleStartupTest extends IntegrationTest {

    private static ConfigurableApplicationContext cloud;
    private static ConfigurableApplicationContext onprem;

    /** 이 테스트의 부모가 띄운 합친 역할의 DB */
    @Autowired private JdbcTemplate combined;

    @BeforeAll
    static void startRoles() throws Exception {
        createDatabases("role_cloud", "role_onprem", "role_old", "role_misconfigured");
        cloud = start("cloud", "role_cloud");
        // 원내가 업무 서버에 실제로 물을 일은 이 테스트에 없다. 주소가 있어야 뜰 뿐이다.
        onprem = start("onprem", "role_onprem", "--app.work-api.base-url=http://localhost:1");
    }

    @AfterAll
    static void stopRoles() {
        if (cloud != null) cloud.close();
        if (onprem != null) onprem.close();
    }

    // ── 무엇이 올라가는가 ───────────────────────────────────

    @Test
    void 원내에는_진료_코드만_올라가고_발급도_Redis_도_실시간_채널도_없다() {
        assertThat(has(onprem, SubjectPhiService.class)).isTrue();
        assertThat(has(onprem, NursingRecordService.class)).isTrue();
        assertThat(has(onprem, HttpWorkRelationAdapter.class)).isTrue();

        assertThat(has(onprem, WorkOrderService.class)).isFalse();
        assertThat(has(onprem, AuthService.class)).isFalse();
        assertThat(has(onprem, RefreshTokenStore.class)).isFalse();
        assertThat(has(onprem, LocalWorkRelationAdapter.class)).isFalse();
        assertThat(has(onprem, AuditLogPartitionMaintainer.class)).isFalse();
        assertThat(has(onprem, StringRedisTemplate.class)).isFalse();
        assertThat(has(onprem, SimpMessagingTemplate.class)).isFalse();

        assertThat(onprem.getBean(JwtTokenProvider.class).canIssue()).isFalse();
    }

    @Test
    void 업무_서버에는_진료_코드가_올라가지_않는다() {
        assertThat(has(cloud, WorkOrderService.class)).isTrue();
        assertThat(has(cloud, AuthService.class)).isTrue();
        assertThat(has(cloud, WorkRelationController.class)).isTrue();
        assertThat(has(cloud, SimpMessagingTemplate.class)).isTrue();

        assertThat(has(cloud, SubjectPhiService.class)).isFalse();
        assertThat(has(cloud, NursingRecordService.class)).isFalse();
        assertThat(has(cloud, EncounterRepository.class)).isFalse();
        assertThat(has(cloud, HttpWorkRelationAdapter.class)).isFalse();

        assertThat(cloud.getBean(JwtTokenProvider.class).canIssue()).isTrue();
    }

    // ── DB 에 무엇이 남는가 ─────────────────────────────────

    @Test
    void 각_역할_DB_에는_자기_테이블만_있다() {
        assertThat(tables(cloud.getBean(JdbcTemplate.class)))
                .containsExactlyInAnyOrderElementsOf(AppBoundary.WORK_TABLES);
        assertThat(tables(onprem.getBean(JdbcTemplate.class)))
                .containsExactlyInAnyOrderElementsOf(AppBoundary.PHI_TABLES);
    }

    @Test
    void 합친_스키마는_두_역할_스키마를_더한_것과_같다() {
        // V1~V14 를 역할별로 다시 쓰지 않은 대가로 이것을 지킨다.
        // 컬럼 하나라도 어긋나면 한 서버일 때와 두 서버일 때 다른 데이터를 저장하게 된다.
        Set<String> union = new HashSet<>(columns(cloud.getBean(JdbcTemplate.class)));
        union.addAll(columns(onprem.getBean(JdbcTemplate.class)));

        assertThat(columns(combined)).isEqualTo(union);
    }

    @Test
    void 합친_DB_의_모든_테이블은_어느_한쪽으로_분류돼_있다() {
        // 분류되지 않은 테이블은 V15 가 어느 쪽에서도 지우지 않아 양쪽 DB 에 남는다
        List<String> classified = new ArrayList<>(AppBoundary.PHI_TABLES);
        classified.addAll(AppBoundary.WORK_TABLES);

        assertThat(tables(combined)).containsExactlyInAnyOrderElementsOf(classified);
    }

    // ── 가드 ────────────────────────────────────────────────

    @Test
    void 원내인데_업무_서버_주소가_없으면_뜨지_않는다() {
        assertThatThrownBy(() -> start("onprem", "role_onprem"))
                .hasStackTraceContaining("업무 서버 주소");
    }

    @Test
    void 원내에_서명용_개인키가_있으면_뜨지_않는다() {
        assertThatThrownBy(() -> start("onprem", "role_onprem",
                "--app.work-api.base-url=http://localhost:1",
                "--jwt.private-key=classpath:keys/local-dev-only-private.pem"))
                .hasStackTraceContaining("개인키");
    }

    @Test
    void 업무_서버에_원내용_주소가_섞이면_뜨지_않는다() {
        assertThatThrownBy(() -> start("cloud", "role_cloud",
                "--app.work-api.base-url=http://localhost:1"))
                .hasStackTraceContaining("업무 역할에 업무 서버 주소");
    }

    @Test
    void 마이그레이션_위치를_잘못_주면_원내_DB_에_업무_테이블이_남은_것을_잡는다() {
        // 코드는 멀쩡히 뜬다. 스키마 가드가 없으면 원내 DB 에 직원 테이블이 그대로 남는다.
        assertThatThrownBy(() -> start("onprem", "role_misconfigured",
                "--app.work-api.base-url=http://localhost:1",
                "--spring.flyway.locations=classpath:db/migration"))
                .hasStackTraceContaining("상대 쪽 테이블");
    }

    @Test
    void 쓰던_합친_DB_에_업무_역할을_붙이면_환자_테이블을_지우지_않고_멈춘다() {
        // 합친 역할로 이미 쓰던 DB 를 흉내 낸다
        Flyway.configure()
                .dataSource(url("role_old"), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JdbcTemplate old = jdbc("role_old");
        old.update("update flyway_schema_history set installed_on = installed_on - interval '2 days'");

        assertThatThrownBy(() -> start("cloud", "role_old"))
                .hasStackTraceContaining("이미 쓰던 DB");

        assertThat(tables(old)).contains("patient", "nursing_note");
    }

    // ── 도우미 ──────────────────────────────────────────────

    private static ConfigurableApplicationContext start(String profile, String database, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + url(database),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379)));
        args.addAll(List.of(extra));

        return new SpringApplicationBuilder(NurseCollabApplication.class)
                .profiles(profile)
                .run(args.toArray(String[]::new));
    }

    private static void createDatabases(String... names) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String name : names) {
                statement.execute("DROP DATABASE IF EXISTS " + name);
                statement.execute("CREATE DATABASE " + name);
            }
        }
    }

    private static String url(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
    }

    private static JdbcTemplate jdbc(String database) {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static boolean has(ConfigurableApplicationContext context, Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }

    private static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                  and table_name not like 'audit\\_log\\_%'
                """, String.class);
    }

    private static Set<String> columns(JdbcTemplate jdbc) {
        return new HashSet<>(jdbc.queryForList("""
                select c.table_name || '.' || c.column_name || ' ' || c.data_type || ' ' || c.is_nullable
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public' and t.table_type = 'BASE TABLE'
                  and c.table_name <> 'flyway_schema_history'
                  and c.table_name not like 'audit\\_log\\_%'
                """, String.class));
    }
}
