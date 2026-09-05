package com.nursecollab.domain.audit.service;

import com.nursecollab.domain.audit.dto.AuditLogResponse;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.repository.PatientRepository;
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
 * 감사 로그 조회.
 * "누가 어떤 환자 정보를 열어봤는가" 에 답할 수 있어야 한다. 관리자만 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final StaffRepository staffRepository;
    private final PatientRepository patientRepository;

    public PageResponse<AuditLogResponse> search(LocalDate from, LocalDate to,
                                                 Long patientId, Long actorId,
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
                patientId, actorId, pageable);

        if (page.getContent().isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        // 행마다 직원과 환자를 따로 조회하면 페이지 크기만큼 쿼리가 나간다. 한 번에 모은다.
        Map<Long, Staff> actors = staffRepository
                .findAllById(distinctIds(page, AuditLog::getActorId))
                .stream().collect(Collectors.toMap(Staff::getId, Function.identity()));

        Map<Long, Patient> patients = patientRepository
                .findAllById(distinctIds(page, AuditLog::getPatientId))
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));

        return PageResponse.of(page.map(log -> {
            Staff actor = actors.get(log.getActorId());
            Patient patient = patients.get(log.getPatientId());
            return AuditLogResponse.of(log,
                    actor == null ? null : new AuditLogResponse.ActorInfo(
                            actor.getId(), actor.getName(), actor.getDepartment().getName()),
                    patient == null ? null : new AuditLogResponse.PatientInfo(
                            patient.getId(), patient.getPatientNo(), patient.getName()));
        }));
    }

    private List<Long> distinctIds(Page<AuditLog> page, Function<AuditLog, Long> extractor) {
        return page.getContent().stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
