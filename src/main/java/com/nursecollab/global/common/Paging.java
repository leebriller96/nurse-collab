package com.nursecollab.global.common;

import org.springframework.data.domain.PageRequest;

/**
 * 목록 API 가 받는 page·size 를 한 곳에서 자른다.
 *
 * 값을 그대로 PageRequest 에 넣으면 size=0 은 500 으로 터지고, size=100000 은 반년치 요청을
 * 한 번에 메모리로 올린다. 화면과 검사 스크립트가 쓰는 가장 큰 값이 200 이라 거기서 자른다.
 * 잘못된 값을 거절하지 않고 눌러 담는 것은, 목록이 안 열리는 것보다 조금 짧게 열리는 쪽이 낫기 때문이다.
 */
public final class Paging {

    public static final int MAX_SIZE = 200;

    private Paging() {}

    public static PageRequest of(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE));
    }
}
