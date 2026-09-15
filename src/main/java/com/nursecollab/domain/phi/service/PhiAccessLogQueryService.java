package com.nursecollab.domain.phi.service;

import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.phi.dto.PhiAccessLogResponse;
import com.nursecollab.domain.phi.entity.PhiAccessLog;
import com.nursecollab.domain.phi.repository.PhiAccessLogRepository;
import com.nursecollab.domain.staff.entity.StaffRole;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A-05 환자 정보 열람 기록. "누가 어떤 환자 정보를 열어봤는가" 에 답한다.
 *
 * 원내 경로(/phi)로만 열린다. 전에는 "보여주려면 클라우드를 거쳐야 하므로 화면에 띄우지 않는다"
 * 고 적어 두었는데 틀린 판단이었다. 브라우저가 원내에 직접 물으면 클라우드는 이 기록을 보지 않는다.
 * 원내망 밖에서는 이 화면이 열리지 않을 뿐이다.
 *
 * 관리자만 본다. 감시 기록을 감시 대상이 볼 수 있으면 기록의 뜻이 없어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhiAccessLogQueryService {

    private final PhiAccessLogRepository repository;
    private final PatientRepository patientRepository;

    public PageResponse<PhiAccessLogResponse> search(LocalDate from, LocalDate to, String patientNo,
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
        Page<PhiAccessLog> page = repository.search(
                fromDate.atStartOfDay(zone).toOffsetDateTime(),
                toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                (patientNo == null || patientNo.isBlank()) ? null : patientNo.trim(),
                pageable);

        // 행마다 환자를 따로 조회하면 페이지 크기만큼 쿼리가 나간다. 한 번에 모은다.
        Map<Long, Patient> patients = patientRepository
                .findAllById(page.getContent().stream()
                        .map(PhiAccessLog::getPatientId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        return PageResponse.of(page.map(log ->
                PhiAccessLogResponse.of(log, patients.get(log.getPatientId()))));
    }
}
