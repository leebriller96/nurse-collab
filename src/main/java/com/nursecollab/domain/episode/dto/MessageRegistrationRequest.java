package com.nursecollab.domain.episode.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 원내가 알린다: 이 요청에 메시지가 하나 달렸다.
 *
 * 내용은 여기 없다. 원내가 본문을 들고 있고 이 열쇠로 가리킨다.
 *
 * @param senderId 토큰의 주체와 같아야 한다. 남의 이름으로 메시지를 달 수 없다.
 */
public record MessageRegistrationRequest(
        @NotNull Long orderId,
        @NotNull UUID messageRef,
        @NotNull Long senderId
) {}
