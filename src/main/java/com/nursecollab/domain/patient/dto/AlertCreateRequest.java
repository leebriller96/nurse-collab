package com.nursecollab.domain.patient.dto;

import com.nursecollab.domain.patient.entity.AlertSeverity;
import com.nursecollab.domain.patient.entity.AlertType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 환자 주의사항 등록.
 *
 * 내용은 비워 둘 수 있다. 종류만으로 뜻이 통하는 것이 있다(금식, 격리).
 * 반대로 "좌측 고관절 인공관절 (2019년 삽입)" 처럼 적어야 쓸모 있는 것도 있어
 * 칸은 열어 둔다.
 */
public record AlertCreateRequest(
        @NotNull(message = "주의사항 종류는 필수입니다.")
        AlertType alertType,

        @NotNull(message = "위험도는 필수입니다.")
        AlertSeverity severity,

        @Size(max = 300, message = "내용은 300자를 넘을 수 없습니다.")
        String content
) {}
