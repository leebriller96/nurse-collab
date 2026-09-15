package com.nursecollab.domain.nursing.controller;

import com.nursecollab.domain.nursing.dto.NursingNoteRequest;
import com.nursecollab.domain.nursing.dto.NursingNoteResponse;
import com.nursecollab.domain.nursing.dto.VitalSignRequest;
import com.nursecollab.domain.nursing.dto.VitalSignResponse;
import com.nursecollab.domain.nursing.entity.NoteType;
import com.nursecollab.domain.nursing.service.NursingRecordService;
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
import java.util.UUID;

/**
 * W-06 활력징후, W-07 간호기록.
 *
 * 경로가 {@code /phi} 아래다. 두 서버로 갈라지면 중계 서버가 경로 앞머리만 보고
 * 원내로 보낼지 정한다. 예전처럼 {@code /encounters/..} 에 두면 이 요청이 클라우드로 가는데,
 * 클라우드에는 이 기록이 없다.
 *
 * 열쇠도 재원 id 가 아니라 가명이다. 주소는 브라우저 기록과 중계 서버 로그에 남는다.
 */
@RestController
@RequestMapping("/api/v1/phi")
@RequiredArgsConstructor
public class NursingRecordController {

    private final NursingRecordService nursingRecordService;

    @PostMapping("/subjects/{subjectRef}/vital-signs")
    public ResponseEntity<VitalSignResponse> recordVitalSign(
            @PathVariable UUID subjectRef,
            @Valid @RequestBody VitalSignRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.recordVitalSign(subjectRef, request, loginStaff));
    }

    @GetMapping("/subjects/{subjectRef}/vital-signs")
    public ResponseEntity<PageResponse<VitalSignResponse>> findVitalSigns(
            @PathVariable UUID subjectRef,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.findVitalSigns(
                subjectRef, from, to, PageRequest.of(page, size), loginStaff));
    }

    @PostMapping("/subjects/{subjectRef}/nursing-notes")
    public ResponseEntity<NursingNoteResponse> writeNote(
            @PathVariable UUID subjectRef,
            @Valid @RequestBody NursingNoteRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.writeNote(subjectRef, request, loginStaff));
    }

    @GetMapping("/subjects/{subjectRef}/nursing-notes")
    public ResponseEntity<PageResponse<NursingNoteResponse>> findNotes(
            @PathVariable UUID subjectRef,
            @RequestParam(required = false) NoteType noteType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(nursingRecordService.findNotes(
                subjectRef, noteType, PageRequest.of(page, size), loginStaff));
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
