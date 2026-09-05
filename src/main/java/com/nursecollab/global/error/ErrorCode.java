package com.nursecollab.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러코드 / HTTP 상태 / 사용자 노출 메시지를 한 곳에서 관리한다.
 * message 는 화면에 그대로 띄울 수 있는 한국어 문장으로 작성한다.
 */
@Getter
public enum ErrorCode {

    // 입력값
    INVALID_INPUT("VAL-001", HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),

    // 인증
    INVALID_CREDENTIALS("AUTH-001", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED("AUTH-002", HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    INACTIVE_ACCOUNT("AUTH-003", HttpStatus.UNAUTHORIZED, "비활성화된 계정입니다. 관리자에게 문의하세요."),

    // 권한
    NOT_RELATED_DEPARTMENT("PERM-001", HttpStatus.FORBIDDEN, "해당 요청에 관여하는 파트가 아닙니다."),
    NOT_ALLOWED_ACTOR("PERM-002", HttpStatus.FORBIDDEN, "이 작업은 상대 파트에서 처리해야 합니다."),
    INSUFFICIENT_ROLE("PERM-003", HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),

    // 재원 / 검사
    ENCOUNTER_NOT_FOUND("ENC-000", HttpStatus.NOT_FOUND, "재원 정보를 찾을 수 없습니다."),
    DISCHARGED_ENCOUNTER("ENC-001", HttpStatus.UNPROCESSABLE_ENTITY, "퇴원한 환자에 대해서는 요청할 수 없습니다."),
    EXAM_TYPE_NOT_FOUND("EXM-001", HttpStatus.NOT_FOUND, "검사 종류를 찾을 수 없습니다."),

    // 이송 요청
    REQUEST_NOT_FOUND("TR-000", HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),
    INVALID_TRANSITION("TR-001", HttpStatus.CONFLICT, "현재 상태에서는 변경할 수 없습니다. 화면을 새로고침해 주세요."),
    VERSION_CONFLICT("TR-002", HttpStatus.CONFLICT, "다른 사용자가 먼저 처리했습니다. 화면을 새로고침해 주세요."),
    REASON_REQUIRED("TR-003", HttpStatus.BAD_REQUEST, "보류 또는 취소 시 사유는 필수입니다."),
    ALREADY_FINISHED("TR-004", HttpStatus.CONFLICT, "이미 종료된 요청입니다."),
    SCHEDULE_REQUIRED("TR-005", HttpStatus.BAD_REQUEST, "접수 시 예정 시각은 필수입니다."),

    // 간호기록
    NOTE_NOT_FOUND("NN-000", HttpStatus.NOT_FOUND, "간호기록을 찾을 수 없습니다."),
    NOTE_NOT_EDITABLE("NN-001", HttpStatus.FORBIDDEN, "본인이 작성한 기록만 수정할 수 있습니다."),
    NOTE_EDIT_WINDOW_CLOSED("NN-002", HttpStatus.UNPROCESSABLE_ENTITY, "작성 후 24시간이 지난 기록은 수정할 수 없습니다. 정정 기록을 새로 남겨 주세요."),
    NOTE_CONTENT_REQUIRED("NN-003", HttpStatus.BAD_REQUEST, "기록 내용은 비워 둘 수 없습니다."),

    // 알림
    NOTIFICATION_NOT_FOUND("NTF-000", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // 기타
    STAFF_NOT_FOUND("STF-001", HttpStatus.NOT_FOUND, "직원 정보를 찾을 수 없습니다."),
    INTERNAL_ERROR("SYS-001", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
