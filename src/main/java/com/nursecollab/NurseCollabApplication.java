package com.nursecollab;

import com.nursecollab.global.config.RoleTypeExcludeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * {@code @SpringBootApplication} 을 풀어 쓴 것이다. 기본 제외 필터 두 개에
 * 역할 필터 하나를 더하려면 이렇게 할 수밖에 없다.
 *
 * 한 빌드가 역할({@code app.role})에 따라 원내 코드만, 또는 업무 코드만 올린다.
 * 무엇이 어느 쪽인지는 {@link com.nursecollab.global.config.AppBoundary} 에 있다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = RoleTypeExcludeFilter.class)})
public class NurseCollabApplication {

    public static void main(String[] args) {
        SpringApplication.run(NurseCollabApplication.class, args);
    }
}
