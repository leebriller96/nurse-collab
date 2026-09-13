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
import com.nursecollab.domain.phi.service.PhiAccessRecorder;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 활력징후와 간호기록.
 *
 * 둘 다 병동이 자기 환자에 대해 남기는 기록이다.
 * 검사실은 검사 때문에 잠깐 관계가 생겼을 뿐이라 병동의 기록에는 접근하지 않는다.
 *
 * <p>누가 열고 썼는지는 원내 접근 기록(phi_access_log)에 남긴다. 전에는 업무 쪽
 * 감사 로그에 쌓였는데, 간호기록 수정 전 내용은 그 자체가 진료정보다.
 * 기록은 따로 커밋되므로({@code REQUIRES_NEW}) 쓰기는 저장을 먼저 확정한 뒤 남긴다.
 * 저장이 실패했는데 "기록했다" 가 남으면 기록을 믿을 수 없다.
 */
@Service
@RequiredArgsConstructor
public class NursingRecordService {

    private final VitalSignRepository vitalSignRepository;
    private final NursingNoteRepository noteRepository;
    private final EncounterRepository encounterRepository;
    private final PhiAccessRecorder phiAccessRecorder;

    // ------------------------------------------------------------------
    // 활력징후
    // ------------------------------------------------------------------

    @Transactional
    public VitalSignResponse recordVitalSign(UUID subjectRef, VitalSignRequest req,
                                             LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(subjectRef, loginStaff, "VITAL_CREATE");

        VitalSign saved = vitalSignRepository.saveAndFlush(VitalSign.record(
                encounter, req.measuredAt(), req.temperature(), req.pulse(), req.respiration(),
                req.sbp(), req.dbp(), req.spo2(), req.painScore(),
                loginStaff.staffId(), loginStaff.name()));
        granted(encounter, loginStaff, "VITAL_CREATE", null);

        return VitalSignResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<VitalSignResponse> findVitalSigns(UUID subjectRef, OffsetDateTime from,
                                                          OffsetDateTime to, Pageable pageable,
                                                          LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(subjectRef, loginStaff, "VITAL_VIEW");
        granted(encounter, loginStaff, "VITAL_VIEW", null);
        return PageResponse.of(vitalSignRepository.search(encounter.getId(), from, to, pageable)
                .map(VitalSignResponse::from));
    }

    // ------------------------------------------------------------------
    // 간호기록
    // ------------------------------------------------------------------

    @Transactional
    public NursingNoteResponse writeNote(UUID subjectRef, NursingNoteRequest req,
                                         LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(subjectRef, loginStaff, "NOTE_CREATE");

        NursingNote saved = noteRepository.saveAndFlush(NursingNote.write(
                encounter, req.noteType(), req.situation(), req.background(),
                req.assessment(), req.recommendation(), req.content(),
                req.recordedAt(), loginStaff.staffId(), loginStaff.name()));
        granted(encounter, loginStaff, "NOTE_CREATE", null);

        return NursingNoteResponse.of(saved, loginStaff.staffId());
    }

    @Transactional(readOnly = true)
    public PageResponse<NursingNoteResponse> findNotes(UUID subjectRef, NoteType noteType,
                                                       Pageable pageable, LoginStaff loginStaff) {
        Encounter encounter = wardEncounter(subjectRef, loginStaff, "NOTE_VIEW");
        granted(encounter, loginStaff, "NOTE_VIEW", null);
        return PageResponse.of(noteRepository.search(encounter.getId(), noteType, pageable)
                .map(note -> NursingNoteResponse.of(note, loginStaff.staffId())));
    }

    /**
     * 기록 수정.
     * 고치기 전 내용을 원내 접근 기록에 남긴다. 별도 이력 테이블을 두지 않고
     * "누가 무엇을 어떻게 바꿨나" 를 한 곳에서 보게 하려는 것이다.
     */
    @Transactional
    public NursingNoteResponse editNote(Long noteId, NursingNoteRequest req, LoginStaff loginStaff) {
        NursingNote note = noteRepository.findDetailById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));

        Encounter encounter = requireOwnWard(note.getEncounter(), loginStaff, "NOTE_EDIT");

        Map<String, Object> before = snapshot(note);
        note.edit(loginStaff.staffId(), req.situation(), req.background(),
                req.assessment(), req.recommendation(), req.content());
        noteRepository.flush();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", note.getId());
        detail.put("before", before);
        detail.put("after", snapshot(note));
        // 적재 실패는 기록기가 삼키고 로그로 남긴다. 수정 자체는 이미 끝났다.
        granted(encounter, loginStaff, "NOTE_EDIT", detail);

        return NursingNoteResponse.of(note, loginStaff.staffId());
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

    private Encounter wardEncounter(UUID subjectRef, LoginStaff loginStaff, String action) {
        Encounter encounter = encounterRepository.findBySubjectRef(subjectRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));
        return requireOwnWard(encounter, loginStaff, action);
    }

    /**
     * 간호기록은 그 환자가 있는 병동의 것이다.
     * 업무 요청으로 잠깐 관계가 생긴 수행 파트는 여기까지 볼 이유가 없다.
     */
    private Encounter requireOwnWard(Encounter encounter, LoginStaff loginStaff, String action) {
        boolean ownWard = encounter.getDepartmentId().equals(loginStaff.departmentId());
        if (!ownWard && loginStaff.role() != StaffRole.ADMIN) {
            // 막힌 시도야말로 조사할 때 제일 보고 싶은 것이다
            phiAccessRecorder.denied(loginStaff, encounter.getSubjectRef(), action, "NOT_RELATED");
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }

    private void granted(Encounter encounter, LoginStaff loginStaff, String action,
                         Map<String, Object> detail) {
        phiAccessRecorder.granted(loginStaff, encounter.getSubjectRef(),
                encounter.getPatient().getId(), action, detail);
    }
}
