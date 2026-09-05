package com.nursecollab.domain.nursing.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.nursing.dto.NursingNoteRequest;
import com.nursecollab.domain.nursing.dto.NursingNoteResponse;
import com.nursecollab.domain.nursing.dto.VitalSignRequest;
import com.nursecollab.domain.nursing.dto.VitalSignResponse;
import com.nursecollab.domain.nursing.entity.NoteType;
import com.nursecollab.domain.nursing.entity.NursingNote;
import com.nursecollab.domain.nursing.entity.VitalSign;
import com.nursecollab.domain.nursing.repository.NursingNoteRepository;
import com.nursecollab.domain.nursing.repository.VitalSignRepository;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.global.audit.AuditLog;
import com.nursecollab.global.audit.AuditRecorder;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 활력징후와 간호기록.
 *
 * 둘 다 병동이 자기 환자에 대해 남기는 기록이다.
 * 검사실은 검사 때문에 잠깐 관계가 생겼을 뿐이라 병동의 기록에는 접근하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NursingRecordService {

    private final VitalSignRepository vitalSignRepository;
    private final NursingNoteRepository noteRepository;
    private final EncounterRepository encounterRepository;
    private final StaffRepository staffRepository;
    private final AuditRecorder auditRecorder;

    // ------------------------------------------------------------------
    // 활력징후
    // ------------------------------------------------------------------

    @Transactional
    public VitalSignResponse recordVitalSign(Long encounterId, VitalSignRequest req,
                                             LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(encounterId, loginStaff);
        Staff recorder = staff(loginStaff.staffId());

        VitalSign saved = vitalSignRepository.save(VitalSign.record(
                encounter, req.measuredAt(), req.temperature(), req.pulse(), req.respiration(),
                req.sbp(), req.dbp(), req.spo2(), req.painScore(), recorder));

        return VitalSignResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<VitalSignResponse> findVitalSigns(Long encounterId, OffsetDateTime from,
                                                          OffsetDateTime to, Pageable pageable,
                                                          LoginStaff loginStaff) {
        wardEncounter(encounterId, loginStaff);
        return PageResponse.of(vitalSignRepository.search(encounterId, from, to, pageable)
                .map(VitalSignResponse::from));
    }

    // ------------------------------------------------------------------
    // 간호기록
    // ------------------------------------------------------------------

    @Transactional
    public NursingNoteResponse writeNote(Long encounterId, NursingNoteRequest req,
                                         LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(encounterId, loginStaff);
        Staff recorder = staff(loginStaff.staffId());

        NursingNote saved = noteRepository.save(NursingNote.write(
                encounter, req.noteType(), req.situation(), req.background(),
                req.assessment(), req.recommendation(), req.content(),
                req.recordedAt(), recorder));

        return NursingNoteResponse.of(saved, loginStaff.staffId());
    }

    @Transactional(readOnly = true)
    public PageResponse<NursingNoteResponse> findNotes(Long encounterId, NoteType noteType,
                                                       Pageable pageable, LoginStaff loginStaff) {
        wardEncounter(encounterId, loginStaff);
        return PageResponse.of(noteRepository.search(encounterId, noteType, pageable)
                .map(note -> NursingNoteResponse.of(note, loginStaff.staffId())));
    }

    /**
     * 기록 수정.
     * 고치기 전 내용을 감사 로그에 남긴다. 별도 이력 테이블을 두지 않고
     * "누가 무엇을 어떻게 바꿨나" 를 감사 로그 한 곳에서 보게 하려는 것이다.
     */
    @Transactional
    public NursingNoteResponse editNote(Long noteId, NursingNoteRequest req, LoginStaff loginStaff) {
        NursingNote note = noteRepository.findDetailById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));

        wardEncounter(note.getEncounter().getId(), loginStaff);
        Staff editor = staff(loginStaff.staffId());

        Map<String, Object> before = snapshot(note);
        note.edit(editor, req.situation(), req.background(),
                req.assessment(), req.recommendation(), req.content());

        recordEdit(note, loginStaff, before);
        return NursingNoteResponse.of(note, loginStaff.staffId());
    }

    private void recordEdit(NursingNote note, LoginStaff loginStaff, Map<String, Object> before) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", snapshot(note));

            auditRecorder.record(AuditLog.of(
                    loginStaff.staffId(), "UPDATE", "NURSING_NOTE", note.getId(),
                    note.getEncounter().getPatient().getId(), null, null, detail));
        } catch (Exception e) {
            // 기록 수정 자체는 이미 끝났다. 감사 적재 실패로 되돌리지는 않되 반드시 남긴다.
            log.error("간호기록 수정 이력 적재 실패. noteId={}", note.getId(), e);
        }
    }

    private Map<String, Object> snapshot(NursingNote note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("situation", note.getSituation());
        map.put("background", note.getBackground());
        map.put("assessment", note.getAssessment());
        map.put("recommendation", note.getRecommendation());
        map.put("content", note.getContent());
        return map;
    }

    /**
     * 간호기록은 그 환자가 있는 병동의 것이다.
     * 이송 요청으로 잠깐 관계가 생긴 검사실은 여기까지 볼 이유가 없다.
     */
    private Encounter wardEncounter(Long encounterId, LoginStaff loginStaff) {
        Encounter encounter = encounterRepository.findByIdWithPatientAndDepartment(encounterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        boolean ownWard = encounter.getDepartment().getId().equals(loginStaff.departmentId());
        if (!ownWard && loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }

    private Staff staff(Long staffId) {
        return staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
    }
}
