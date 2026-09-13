package com.nursecollab.domain.episode.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 원내가 알린다: 침대가 찼다.
 *
 * 원내에서 밖으로 나가는 유일한 쓰기다. 이름도 진단명도 거동 여부도 여기 없다.
 * 칸을 늘리고 싶어지면 그 값이 사람의 건강 상태인지 먼저 따진다.
 */
public record EpisodeRegistrationRequest(
        @NotNull UUID subjectRef,
        @NotNull Long departmentId,
        @Size(max = 10) String roomNo,
        @Size(max = 10) String bedNo,
        @NotNull OffsetDateTime admittedAt
) {}
