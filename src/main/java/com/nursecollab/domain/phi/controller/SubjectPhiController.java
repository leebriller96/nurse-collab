package com.nursecollab.domain.phi.controller;

import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.phi.dto.SubjectBrief;
import com.nursecollab.domain.phi.dto.SubjectPhi;
import com.nursecollab.domain.phi.service.SubjectPhiService;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 원내에만 있는 것들.
 *
 * 업무 API(`/api/v1/...`)와 경로를 나눠 둔 이유는 나중에 <b>다른 서버</b>가 되기 때문이다.
 * 지금은 같은 앱이 둘 다 들고 있지만, 이 경로에 닿는 데이터는 전부 진료정보다.
 * 경로가 갈려 있어야 무엇이 원내에만 있어야 하는지 한눈에 보이고,
 * 떼어낼 때 옮길 것과 남길 것이 분명해진다.
 */
@RestController
@RequestMapping("/api/v1/phi")
@RequiredArgsConstructor
public class SubjectPhiController {

    private final SubjectPhiService subjectPhiService;

    /** 가명 하나를 사람으로 되돌린다 */
    @GetMapping("/subjects/{subjectRef}")
    public ResponseEntity<SubjectPhi> findOne(@PathVariable UUID subjectRef,
                                              @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(subjectPhiService.findOne(subjectRef, loginStaff));
    }

    /**
     * 목록 한 화면을 한 번에 채운다.
     *
     * 조회인데 POST 인 이유: 큐 한 화면이면 가명이 스무 개 넘게 붙는다.
     * 주소창에 담으면 길이 제한에 걸리고, 중계 서버 접근 로그에 그대로 쌓인다.
     * 가명이라 사람을 알아볼 수는 없지만, 굳이 흘려 둘 이유도 없다.
     */
    @PostMapping("/subjects/brief")
    public ResponseEntity<List<SubjectBrief>> findBrief(@RequestBody List<UUID> subjectRefs,
                                                        @AuthenticationPrincipal LoginStaff staff) {
        return ResponseEntity.ok(subjectPhiService.findBrief(subjectRefs, staff));
    }

    /**
     * 이 업무 전에 확인할 것.
     * 확인 항목은 업무 쪽이 알고 있으므로 부르는 쪽이 들고 온다.
     */
    @GetMapping("/subjects/{subjectRef}/checklist")
    public ResponseEntity<List<ChecklistWarning>> checklist(
            @PathVariable UUID subjectRef,
            @RequestParam(required = false) List<AlertType> required,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(subjectPhiService.checklist(subjectRef, required, loginStaff));
    }

    /**
     * 이름으로 가명을 찾는다.
     *
     * 업무 목록에서 이름으로 검색하려면 먼저 여기서 가명을 받아 그것으로 걸러야 한다.
     * 이름이 업무 쪽으로 넘어가지 않는 이유가 이것이다.
     */
    @GetMapping("/subjects/search")
    public ResponseEntity<List<UUID>> search(@RequestParam String name,
                                             @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(subjectPhiService.searchRefs(name, loginStaff));
    }
}
