package com.nursecollab.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
// 자동 구성의 저장소 스캔에는 필터를 걸 수 없다. 직접 켜고 역할 필터를 건다.
@EnableJpaRepositories(basePackages = "com.nursecollab",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = RoleTypeExcludeFilter.class))
public class JpaConfig {

    /**
     * 기본 제공자는 LocalDateTime 을 돌려주는데 엔티티는 OffsetDateTime 을 쓴다.
     * 타입 변환에 기대지 않도록 처음부터 OffsetDateTime 을 주입한다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }

    /**
     * 이 역할의 엔티티만 등록한다.
     *
     * 스키마 검증(ddl-auto: validate)이 등록된 엔티티 전부를 DB 와 맞춰 본다. 원내 DB 에는
     * 업무 쪽 테이블이 없으므로, 업무 쪽 엔티티가 섞여 있으면 원내가 뜨지 않는다.
     * 저장소를 걸러도 엔티티는 따로 스캔되기 때문에 여기서도 거른다.
     */
    @Bean
    public static PersistenceManagedTypes persistenceManagedTypes(ResourceLoader resourceLoader,
                                                                  Environment environment) {
        AppRole role = AppRole.from(environment);
        return new PersistenceManagedTypesScanner(resourceLoader, role::includes)
                .scan("com.nursecollab");
    }
}
