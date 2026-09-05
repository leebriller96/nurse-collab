package com.nursecollab.domain.patient.entity;

/**
 * 환자 주의사항 유형.
 *
 * 검사 전 경고 문구를 여기 함께 두는 이유: 문구가 서비스 코드로 흩어지면
 * 유형을 추가할 때 어디를 고쳐야 하는지 알 수 없게 된다. 상태 전이 규칙과 같은 원칙이다.
 */
public enum AlertType {

    METAL_IMPLANT("체내 금속물", "MRI 금기 가능성. 시행 전 확인 필요."),
    CONTRAST_ALLERGY("조영제 알레르기", "조영제 사용 검사입니다. 투여 전 반드시 확인하세요."),
    DRUG_ALLERGY("약물 알레르기", "투약 전 알레르기 이력을 확인하세요."),
    ISOLATION("격리 필요", "격리 환자입니다. 이송 동선과 소독 절차를 확인하세요."),
    FALL_RISK("낙상 위험", "낙상 위험 환자입니다. 이송 시 동반이 필요합니다."),
    NPO("금식 중", "금식 상태를 확인하세요."),
    OXYGEN("산소 필요", "산소 공급이 필요한 환자입니다. 이송 장비를 확인하세요."),
    CLAUSTROPHOBIA("폐소공포", "폐소공포 이력이 있습니다. 검사 전 진정 여부를 확인하세요.");

    private final String label;
    private final String warningMessage;

    AlertType(String label, String warningMessage) {
        this.label = label;
        this.warningMessage = warningMessage;
    }

    public String getLabel() {
        return label;
    }

    /** 이 유형이 검사의 필수 확인 항목에 걸렸을 때 검사실 화면에 띄울 문구 */
    public String getWarningMessage() {
        return warningMessage;
    }
}
