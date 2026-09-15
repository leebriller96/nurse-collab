package com.nursecollab.domain.episode.controller;

import com.nursecollab.domain.episode.dto.CareEpisodeSummary;
import com.nursecollab.domain.episode.service.CareEpisodeQueryService;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 병동의 침대 목록 (W-01 의 업무 쪽 절반).
 *
 * 이 응답에는 사람이 없다. 감사 로그도 남기지 않는다 —
 * 환자 정보를 열어본 것이 아니기 때문이다. 열어본 기록은 {@code /phi} 쪽에 남는다.
 */
@RestController
@RequestMapping("/api/v1/care-episodes")
@RequiredArgsConstructor
public class CareEpisodeController {

    private final CareEpisodeQueryService careEpisodeQueryService;

    @GetMapping
    public ResponseEntity<List<CareEpisodeSummary>> findAll(
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(careEpisodeQueryService.findInDepartment(departmentId, loginStaff));
    }

    @GetMapping("/{subjectRef}")
    public ResponseEntity<CareEpisodeSummary.Detail> findOne(
            @PathVariable UUID subjectRef,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(careEpisodeQueryService.findOne(subjectRef, loginStaff));
    }
}
