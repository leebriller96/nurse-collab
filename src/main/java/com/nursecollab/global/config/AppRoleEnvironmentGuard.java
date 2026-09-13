package com.nursecollab.global.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 역할과 설정이 어긋나면 <b>빈을 만들기 전에</b> 멈춘다.
 *
 * 빈을 만든 뒤에 보면 "WorkRelationPort 빈이 없다" 같은 엉뚱한 오류가 먼저 난다.
 * 원인은 주소 한 줄인데 스택 트레이스는 의존성 주입 이야기를 한다.
 *
 * <p>설정 파일을 다 읽은 뒤에 돌아야 하므로 순서를 맨 뒤로 둔다.
 * META-INF/spring.factories 에 등록돼 있다.
 */
public class AppRoleEnvironmentGuard implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        AppRole role = AppRole.from(environment);
        String workApi = environment.getProperty("app.work-api.base-url", "");
        String privateKey = environment.getProperty("jwt.private-key", "");

        switch (role) {
            case ONPREM -> {
                if (workApi.isBlank()) {
                    throw new IllegalStateException("""
                            원내 역할인데 업무 서버 주소(WORK_API_BASE_URL)가 없습니다.
                            원내는 "이 환자에 우리 파트로 온 요청이 있는가" 를 업무 서버에 물어야
                            검사실에 환자 정보를 열어 줄 수 있습니다.""");
                }
                if (!privateKey.isBlank()) {
                    throw new IllegalStateException("""
                            원내 역할에 토큰 서명용 개인키(JWT_PRIVATE_KEY_FILE)가 설정돼 있습니다.
                            원내는 토큰을 검증만 합니다. 개인키를 두면 원내가 뚫렸을 때
                            관리자 토큰을 만들어 업무 서버까지 열 수 있습니다. 공개키만 넘기세요.""");
                }
            }
            case CLOUD -> {
                if (!workApi.isBlank()) {
                    throw new IllegalStateException("""
                            업무 역할에 업무 서버 주소(WORK_API_BASE_URL)가 설정돼 있습니다.
                            이 주소는 원내 서버가 업무 서버를 부를 때만 씁니다.
                            원내용 설정이 업무 서버에 섞여 들어온 것으로 보입니다.""");
                }
            }
            case COMBINED -> {
                // 한 서버가 둘 다 든다. 어긋날 설정이 없다.
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
