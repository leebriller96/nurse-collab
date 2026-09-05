package com.nursecollab.domain.nursing.controller;

import com.nursecollab.domain.nursing.dto.NursingNoteRequest;
import com.nursecollab.domain.nursing.dto.NursingNoteResponse;
import com.nursecollab.domain.nursing.dto.VitalSignRequest;
import com.nursecollab.domain.nursing.dto.VitalSignResponse;
import com.nursecollab.domain.nursing.entity.NoteType;
import com.nursecollab.domain.nursing.service.NursingRecordService;
import com.nursecollab.global.audit.Audited;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NursingRecordController {

    private final NursingRecordService nursingRecordService;

    @PostMapping("/encounters/{encounterId}/vital-signs")
    @Audited(action = "CREATE", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<VitalSignResponse> recordVitalSign(
            @PathVariable Long encounterId,
            @Valid @RequestBody VitalSignRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.recordVitalSign(encounterId, request, loginStaff));
    }

    @GetMapping("/encounters/{encounterId}/vital-signs")
    @Audited(action = "VIEW", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<PageResponse<VitalSignResponse>> findVitalSigns(
            @PathVariable Long encounterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.findVitalSigns(
                encounterId, from, to, PageRequest.of(page, size), loginStaff));
    }

    @PostMapping("/encounters/{encounterId}/nursing-notes")
    @Audited(action = "CREATE", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<NursingNoteResponse> writeNote(
            @PathVariable Long encounterId,
            @Valid @RequestBody NursingNoteRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.writeNote(encounterId, request, loginStaff));
    }

    @GetMapping("/encounters/{encounterId}/nursing-notes")
    @Audited(action = "VIEW", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<PageResponse<NursingNoteResponse>> findNotes(
            @PathVariable Long encounterId,
            @RequestParam(required = false) NoteType noteType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.findNotes(
                encounterId, noteType, PageRequest.of(page, size), loginStaff));
    }

    /** 삭제는 없다. 24시간이 지난 기록은 정정 기록을 새로 남긴다. */
    @PutMapping("/nursing-notes/{noteId}")
    public ResponseEntity<NursingNoteResponse> editNote(
            @PathVariable Long noteId,
            @Valid @RequestBody NursingNoteRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.editNote(noteId, request, loginStaff));
    }
}
