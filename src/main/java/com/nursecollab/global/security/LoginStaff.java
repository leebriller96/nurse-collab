package com.nursecollab.global.security;

import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;

/**
 * 인증된 요청에서 꺼내 쓰는 주체.
 * 컨트롤러는 @AuthenticationPrincipal 로 이걸 받는다.
 *
 * 소속 파트 정보를 토큰에 실어두는 이유: 요청마다 staff 를 다시 조회하지 않기 위해서다.
 * 다만 권한 판정의 최종 근거는 토큰이 아니라 DB 라는 점을 잊으면 안 된다.
 * 소속이 바뀌면 토큰이 만료될 때까지 옛 소속이 남는다.
 */
public record LoginStaff(
        Long staffId,
        String loginId,
        String name,
        StaffRole role,
        Long departmentId,
        DeptType deptType
) {
    public static LoginStaff from(Staff staff) {
        return new LoginStaff(
                staff.getId(),
                staff.getLoginId(),
                staff.getName(),
                staff.getRole(),
                staff.getDepartment().getId(),
                staff.getDepartment().getDeptType());
    }
}
