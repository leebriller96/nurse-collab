package com.nursecollab.domain.stats.controller;

import com.nursecollab.domain.stats.dto.WaitingTimeStats;
import com.nursecollab.domain.stats.service.StatsQueryService;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService statsQueryService;

    /** 조회 범위는 파라미터가 아니라 역할이 정한다. 일반 간호사는 아예 볼 수 없다. */
    @GetMapping("/waiting-time")
    public ResponseEntity<WaitingTimeStats> waitingTime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(statsQueryService.waitingTime(from, to, loginStaff));
    }
}
