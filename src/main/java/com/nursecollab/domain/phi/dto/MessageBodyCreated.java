package com.nursecollab.domain.phi.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 남긴 메시지의 열쇠. 내용은 화면이 이미 들고 있으므로 돌려주지 않는다. */
public record MessageBodyCreated(UUID messageRef, OffsetDateTime createdAt) {}
