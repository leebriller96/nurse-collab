package com.nursecollab.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정해진 시각에 도는 작업을 켠다.
 *
 * 지금은 감사 로그 파티션 정리 하나뿐이다.
 * 메인 클래스에 붙이지 않고 따로 둔 것은, 무엇 때문에 켰는지 남기기 위해서다.
 *
 * 인스턴스를 여러 대로 늘리면 같은 작업이 동시에 돈다.
 * 파티션 생성은 여러 번 불러도 안전하게 만들어 두었지만,
 * 그렇지 않은 작업을 여기 추가할 때는 잠금이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
