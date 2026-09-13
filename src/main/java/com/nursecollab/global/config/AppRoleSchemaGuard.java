package com.nursecollab.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 마이그레이션이 끝난 뒤, 이 DB 에 상대 쪽 테이블이 없는지 본다.
 *
 * 역할 필터는 코드를 거를 뿐 DB 는 모른다. 마이그레이션 위치를 잘못 주거나 합친 DB 에
 * 원내 서버를 붙이면 코드는 멀쩡히 뜨는데 <b>업무 DB 에 환자 테이블이 그대로 남는다.</b>
 * 화면은 문제없이 돌아서 아무도 모른다. 그래서 뜨지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class AppRoleSchemaGuard implements ApplicationRunner {

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        AppRole role = AppRole.from(environment);

        List<String> present = role.forbiddenTables().stream()
                .filter(table -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "select to_regclass(?) is not null", Boolean.class, "public." + table)))
                .toList();

        if (!present.isEmpty()) {
            throw new IllegalStateException("""
                    %s 역할의 DB 에 상대 쪽 테이블이 있습니다: %s
                    합친 DB 에 붙었거나 마이그레이션 위치(spring.flyway.locations)가 역할과 맞지 않습니다.
                    진료 테이블이 업무 DB 에 남아 있으면 나눈 의미가 없습니다."""
                    .formatted(role.name().toLowerCase(), present));
        }
    }
}
