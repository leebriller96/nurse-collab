package com.nursecollab.global.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 요청을 감사 로그에 남긴다.
 *
 * 업무 쪽 행위(기준 정보 변경 등)에 붙인다. 환자 정보를 열어본 기록은 여기가 아니라
 * 원내 phi_access_log 에 남는다. 진료정보가 실제로 나간 곳이 원내이기 때문이다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** CREATE / UPDATE / DEACTIVATE. 로그인 계열(LOGIN / LOGIN_FAILED / LOGOUT)은 AuthService 가 직접 남긴다 */
    String action();

    /** DEPARTMENT / STAFF / SERVICE_ITEM */
    String targetType();

    /** 대상 식별자가 담긴 경로 변수 이름. 없으면(만들기) 응답의 id 를 쓴다 */
    String targetIdParam() default "id";
}
