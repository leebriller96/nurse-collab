package com.nursecollab.global.config;

import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Locale;

/**
 * 이 프로세스가 무엇으로 떴는가. 한 빌드를 세 가지로 띄운다.
 *
 * <ul>
 *   <li>{@code COMBINED} — 한 서버가 둘 다. 로컬 개발, 테스트, 공개 데모.</li>
 *   <li>{@code CLOUD} — 업무 서버. 인증을 맡고 진료 테이블을 모른다.</li>
 *   <li>{@code ONPREM} — 원내 서버. 진료정보만 들고, 토큰은 검증만 한다.</li>
 * </ul>
 *
 * 역할마다 빌드를 따로 만들지 않는 이유: 빌드가 둘이면 두 쪽이 서로 다른 커밋에서
 * 나와도 알 수 없다. 원내와 클라우드가 같은 규칙표를 들고 있다는 보장이 사라진다.
 */
public enum AppRole {

    COMBINED, CLOUD, ONPREM;

    public static final String PROPERTY = "app.role";

    public static AppRole from(Environment environment) {
        String raw = environment.getProperty(PROPERTY, "combined");
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 오타로 뜨면 조용히 합친 역할이 되는 편이 제일 위험하다. 원내에 전부 올라간다.
            throw new IllegalStateException(
                    "app.role 은 combined, cloud, onprem 중 하나여야 합니다. 받은 값: " + raw, e);
        }
    }

    /** 이 역할로 뜰 때 이 클래스를 올리는가 */
    public boolean includes(String className) {
        return switch (this) {
            case COMBINED -> true;
            case CLOUD -> !AppBoundary.isPhi(className);
            case ONPREM -> !AppBoundary.isWork(className);
        };
    }

    /** 이 역할의 DB 에 있으면 안 되는 테이블 */
    public List<String> forbiddenTables() {
        return switch (this) {
            case COMBINED -> List.of();
            case CLOUD -> AppBoundary.PHI_TABLES;
            case ONPREM -> AppBoundary.WORK_TABLES;
        };
    }
}
