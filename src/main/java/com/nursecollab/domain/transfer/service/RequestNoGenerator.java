package com.nursecollab.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * TR20260904-0001 형식의 요청번호를 만든다.
 *
 * 발번을 DB 에 맡기는 이유: 두 병동이 같은 순간에 등록해도 번호가 겹치면 안 되기 때문이다.
 * 호출한 트랜잭션 안에서 실행되므로, 요청 생성이 롤백되면 번호도 함께 되돌아간다.
 */
@Component
@RequiredArgsConstructor
public class RequestNoGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String NEXT_NO = """
            insert into request_no_sequence (date_key, last_no) values (?, 1)
            on conflict (date_key) do update set last_no = request_no_sequence.last_no + 1
            returning last_no
            """;

    private final JdbcTemplate jdbcTemplate;

    public String generate(LocalDate date) {
        Integer sequence = jdbcTemplate.queryForObject(NEXT_NO, Integer.class, Date.valueOf(date));
        return "TR" + date.format(DATE_PART) + "-" + String.format("%04d", sequence);
    }
}
