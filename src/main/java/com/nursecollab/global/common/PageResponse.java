package com.nursecollab.global.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** 목록 응답 공통 형식. Spring 의 Page 를 그대로 직렬화하면 불필요한 필드가 따라오므로 필요한 것만 추린다. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
