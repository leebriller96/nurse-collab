package com.nursecollab.domain.department.entity;

/**
 * 파트 유형. 프론트가 요청하는 쪽 화면과 수행하는 쪽 화면을 가르는 기준이 된다.
 * 병동만 요청하는 쪽이고, ADMIN 을 뺀 나머지는 전부 수행하는 쪽이다.
 */
public enum DeptType {

    WARD("병동"),
    EXAM("검사실"),
    OR("수술실"),
    ICU("중환자실"),
    ER("응급실"),

    // 이송 말고 다른 업무를 받는 파트들
    LAB("진단검사의학과"),
    PHARMACY("약제부"),
    BIOMED("의공학팀"),

    /**
     * 진료 파트가 아닌 관리 부서.
     * 시스템 관리자도 소속이 있어야 하는데, 병동에 넣으면 그 병동의 업무 요청에
     * 요청 파트로 엮여버리기 때문에 따로 둔다.
     */
    ADMIN("관리부서");

    private final String label;

    DeptType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
