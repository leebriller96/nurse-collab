package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.workorder.entity.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * TR20260904-0001 형식의 요청번호를 만든다. 앞 두 글자는 업무 종류다.
 *
 * 발번을 DB 에 맡기는 이유: 두 병동이 같은 순간에 등록해도 번호가 겹치면 안 되기 때문이다.
 * 호출한 트랜잭션 안에서 실행되므로, 요청 생성이 롤백되면 번호도 함께 되돌아간다.
 *
 * 일련번호는 종류별로 센다. 한 통에서 뽑으면 TR-0001 다음에 SP-0002 가 나와서
 * 번호가 빠진 것처럼 보이고, 없는 0001 을 찾게 된다.
 */
@Component
@RequiredArgsConstructor
public class RequestNoGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String NEXT_NO = """
            insert into request_no_sequence (date_key, order_type, last_no) values (?, ?, 1)
            on conflict (date_key, order_type)
                do update set last_no = request_no_sequence.last_no + 1
            returning last_no
            """;

    private final JdbcTemplate jdbcTemplate;

    public String generate(OrderType orderType, LocalDate date) {
        Integer sequence = jdbcTemplate.queryForObject(
                NEXT_NO, Integer.class, Date.valueOf(date), orderType.name());
        return orderType.getRequestNoPrefix()
                + date.format(DATE_PART) + "-" + String.format("%04d", sequence);
    }
}
