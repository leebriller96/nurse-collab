package com.nursecollab.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 공통 기반.
 *
 * 진짜 PostgreSQL 을 띄운다. H2 로는 JSONB·파티셔닝 문법이 깨져서
 * V1 마이그레이션 자체가 돌지 않는다.
 *
 * 컨테이너를 여기 모아 둔 이유는 테스트 클래스마다 따로 띄우지 않기 위해서다.
 * 클래스마다 선언하면 @ServiceConnection 이 매번 다른 접속 정보를 주입해
 * 스프링 컨텍스트 캐시까지 갈라진다.
 *
 * @Testcontainers 와 @Container 를 쓰지 않는 것은 의도한 것이다.
 * 그 조합은 컨테이너 수명을 "테스트 클래스 하나" 에 맞춘다. 필드가 기반 클래스에
 * 하나뿐이어도 먼저 끝난 클래스가 컨테이너를 내려버리고, 캐시된 스프링 컨텍스트는
 * 죽은 DB 를 계속 붙들고 있다. 뒤에 도는 클래스는 커넥션 풀이 빈 채로
 * 30초씩 기다리다 전부 실패한다.
 *
 * 그래서 static 블록에서 직접 띄우고 JVM 이 끝날 때까지 살려 둔다.
 * 정리는 Testcontainers 의 Ryuk 컨테이너가 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        // @ServiceConnection 은 컨텍스트를 만들 때 host/port 를 읽는다. 그 전에 떠 있어야 한다.
        POSTGRES.start();
        REDIS.start();
    }
}
