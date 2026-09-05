package com.nursecollab.global.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 요청을 감사 로그에 남긴다.
 *
 * 수정만 남기는 것이 아니다. 환자 정보를 열어본 것 자체가 기록 대상이다.
 * 의료 시스템에서 "누가 어떤 환자 정보를 봤는가" 는 반드시 답할 수 있어야 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** VIEW / CREATE / UPDATE / DELETE / LOGIN */
    String action();

    /** ENCOUNTER / TRANSFER_REQUEST / NURSING_NOTE 등 */
    String targetType();

    /** 대상 식별자가 담긴 경로 변수 이름 */
    String targetIdParam() default "id";
}
