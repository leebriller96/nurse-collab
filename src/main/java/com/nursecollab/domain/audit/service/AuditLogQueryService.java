package com.nursecollab.domain.audit.service;

import com.nursecollab.domain.audit.dto.AuditLogResponse;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.global.audit.AuditLog;
import com.nursecollab.global.audit.AuditLogRepository;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 업무 쪽 감사 로그 조회. 관리자만 본다.
 *
 * 환자 정보 열람 기록은 여기 없다. 원내(phi_access_log)에 남고 A-05 화면도 그쪽을 읽는다.
 * 전에는 이 서비스가 환자 저장소를 읽어 이름을 붙였는데, 업무 쪽이 진료정보를 읽는
 * 유일한 자리였다. 경계 테스트의 업무 쪽 목록에 이 패키지가 빠져 있어서 잡히지 않았다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final StaffRepository staffRepository;

    public PageResponse<AuditLogResponse> search(LocalDate from, LocalDate to, Long actorId,
                                                 Pageable pageable, LoginStaff loginStaff) {
        if (loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ROLE);
        }

        LocalDate fromDate = (from == null) ? LocalDate.now() : from;
        LocalDate toDate = (to == null) ? fromDate : to;
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ZoneId zone = ZoneId.systemDefault();
        Page<AuditLog> page = auditLogRepository.search(
                fromDate.atStartOfDay(zone).toOffsetDateTime(),
                toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                null, actorId, pageable);

        if (page.getContent().isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        // 행마다 직원을 따로 조회하면 페이지 크기만큼 쿼리가 나간다. 한 번에 모은다.
        Map<Long, Staff> actors = staffRepository
                .findAllById(page.getContent().stream()
                        .map(AuditLog::getActorId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(Staff::getId, Function.identity()));

        return PageResponse.of(page.map(log -> {
            Staff actor = actors.get(log.getActorId());
            return AuditLogResponse.of(log,
                    actor == null ? null : new AuditLogResponse.ActorInfo(
                            actor.getId(), actor.getName(), actor.getDepartment().getName()));
        }));
    }
}
