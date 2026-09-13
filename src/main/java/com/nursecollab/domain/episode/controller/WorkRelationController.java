package com.nursecollab.domain.episode.controller;

import com.nursecollab.domain.episode.dto.ActiveSubjectsRequest;
import com.nursecollab.domain.episode.dto.ActiveSubjectsResponse;
import com.nursecollab.domain.episode.dto.EpisodeRegistrationRequest;
import com.nursecollab.domain.episode.service.WorkRelationService;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 원내 서버가 업무 서버에 묻는 통로. 화면은 부르지 않는다.
 *
 * 원내는 사용자의 토큰을 그대로 실어 온다. 서버용 자격증명을 따로 두면 그것 하나로
 * 모든 파트에 대해 물을 수 있게 된다. 사용자 토큰이면 그 사람이 물을 수 있는 만큼만 묻는다.
 */
@RestController
@RequestMapping("/api/v1/work-relations")
@RequiredArgsConstructor
public class WorkRelationController {

    private final WorkRelationService workRelationService;

    /** 조회인데 POST 인 이유는 목록 이름 채우기와 같다. 가명 수십 개를 주소에 싣지 않는다. */
    @PostMapping("/active-subjects")
    public ResponseEntity<ActiveSubjectsResponse> activeSubjects(
            @Valid @RequestBody ActiveSubjectsRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(workRelationService.activeSubjects(request, loginStaff));
    }

    @PostMapping("/episodes")
    public ResponseEntity<Void> registerEpisode(
            @Valid @RequestBody EpisodeRegistrationRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        workRelationService.register(request, loginStaff);
        return ResponseEntity.noContent().build();
    }
}
