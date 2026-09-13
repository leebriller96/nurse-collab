package com.nursecollab.domain.episode.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 원내가 묻는다: 이 가명들 중 이 파트로 온 진행중 요청이 있는 것은.
 *
 * @param subjectRefs 한 화면 분량. 상한을 두는 이유는 이 통로로 병원 전체를
 *                    한 번에 훑지 못하게 하려는 것이다.
 */
public record ActiveSubjectsRequest(
        @NotNull Long departmentId,
        @NotNull @Size(max = 500) List<UUID> subjectRefs
) {}
