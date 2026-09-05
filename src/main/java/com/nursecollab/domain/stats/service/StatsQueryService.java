package com.nursecollab.domain.stats.service;

import com.nursecollab.domain.stats.dto.WaitingTimeStats;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 대기시간 통계.
 *
 * 집계는 SQL 로 한다. 요청을 전부 읽어와 애플리케이션에서 평균을 내면
 * 건수가 늘어나는 만큼 그대로 느려진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsQueryService {

    /**
     * 요청 → 접수까지 걸린 시간. 접수 이력 중 가장 이른 것을 기준으로 한다.
     * 보류됐다가 다시 접수되면 접수 이력이 두 번 쌓이는데, 이때 "처음 받아준 시각" 이 맞다.
     */
    private static final String FIRST_ACCEPTED = """
            left join lateral (
                select min(e.occurred_at) as accepted_at
                from transfer_event e
                where e.request_id = r.id and e.to_status = 'ACCEPTED'
            ) a on true
            """;

    private final JdbcTemplate jdbcTemplate;

    public WaitingTimeStats waitingTime(LocalDate from, LocalDate to, LoginStaff loginStaff) {
        Long departmentId = resolveScope(loginStaff);

        LocalDate fromDate = (from == null) ? LocalDate.now() : from;
        LocalDate toDate = (to == null) ? fromDate : to;
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ZoneId zone = ZoneId.systemDefault();
        Timestamp start = Timestamp.from(fromDate.atStartOfDay(zone).toInstant());
        // to 는 그 날까지 포함해야 하므로 다음 날 0시 직전까지 본다
        Timestamp end = Timestamp.from(toDate.plusDays(1).atStartOfDay(zone).toInstant());

        return new WaitingTimeStats(
                new WaitingTimeStats.Period(fromDate, toDate),
                overall(start, end, departmentId),
                byDepartment(start, end, departmentId),
                byHour(start, end, departmentId));
    }

    private WaitingTimeStats.Overall overall(Timestamp start, Timestamp end, Long departmentId) {
        String sql = """
                select count(*) as total_requests,
                       round(avg(extract(epoch from (a.accepted_at - r.requested_at)) / 60)) as avg_waiting,
                       round(avg(extract(epoch from (r.completed_at - r.requested_at)) / 60)) as avg_total
                from transfer_request r
                """ + FIRST_ACCEPTED + """
                where r.requested_at >= ? and r.requested_at < ?
                """ + departmentFilter(departmentId);

        return jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> new WaitingTimeStats.Overall(
                        rs.getLong("total_requests"),
                        intOrNull(rs.getObject("avg_waiting")),
                        intOrNull(rs.getObject("avg_total"))),
                args(start, end, departmentId));
    }

    private List<WaitingTimeStats.ByDepartment> byDepartment(Timestamp start, Timestamp end,
                                                             Long departmentId) {
        String sql = """
                select d.id   as department_id,
                       d.name as department_name,
                       count(*) as request_count,
                       round(avg(extract(epoch from (a.accepted_at - r.requested_at)) / 60)) as avg_waiting,
                       count(h.request_id) as hold_count
                from transfer_request r
                join department d on d.id = r.to_department_id
                """ + FIRST_ACCEPTED + """
                left join lateral (
                    select 1 as request_id
                    from transfer_event e
                    where e.request_id = r.id and e.to_status = 'ON_HOLD'
                    limit 1
                ) h on true
                where r.requested_at >= ? and r.requested_at < ?
                """ + departmentFilter(departmentId) + """
                group by d.id, d.name
                order by avg_waiting desc nulls last, request_count desc
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new WaitingTimeStats.ByDepartment(
                        rs.getLong("department_id"),
                        rs.getString("department_name"),
                        rs.getLong("request_count"),
                        intOrNull(rs.getObject("avg_waiting")),
                        rs.getLong("hold_count")),
                args(start, end, departmentId));
    }

    private List<WaitingTimeStats.ByHour> byHour(Timestamp start, Timestamp end, Long departmentId) {
        String sql = """
                select extract(hour from r.requested_at)::int as hour, count(*) as request_count
                from transfer_request r
                where r.requested_at >= ? and r.requested_at < ?
                """ + departmentFilter(departmentId) + """
                group by hour
                order by hour
                """;

        List<WaitingTimeStats.ByHour> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> new WaitingTimeStats.ByHour(
                        rs.getInt("hour"), rs.getLong("request_count")),
                args(start, end, departmentId));

        // 요청이 없던 시간대도 0 으로 채워야 그래프에 빈 구간이 생기지 않는다
        List<WaitingTimeStats.ByHour> filled = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            int h = hour;
            filled.add(rows.stream().filter(r -> r.hour() == h).findFirst()
                    .orElse(new WaitingTimeStats.ByHour(h, 0)));
        }
        return filled;
    }

    /**
     * 일반 간호사는 통계를 볼 수 없다.
     * 수간호사는 자기 파트만, 관리자는 전체를 본다.
     */
    private Long resolveScope(LoginStaff loginStaff) {
        return switch (loginStaff.role()) {
            case ADMIN -> null;
            case HEAD_NURSE -> loginStaff.departmentId();
            case NURSE -> throw new BusinessException(ErrorCode.INSUFFICIENT_ROLE);
        };
    }

    /**
     * 수간호사가 보는 범위는 "우리 파트가 관여한 요청" 이다.
     * 수행 파트로만 거르면 병동 수간호사는 자기 병동이 보낸 요청을 한 건도 못 본다.
     */
    private String departmentFilter(Long departmentId) {
        return departmentId == null
                ? ""
                : "  and (r.to_department_id = ? or r.from_department_id = ?)\n";
    }

    private Object[] args(Timestamp start, Timestamp end, Long departmentId) {
        return departmentId == null
                ? new Object[]{start, end}
                : new Object[]{start, end, departmentId, departmentId};
    }

    private Integer intOrNull(Object value) {
        return (value == null) ? null : ((Number) value).intValue();
    }
}
