-- =========================================================
-- V18__add_delay_notified_at.sql  (업무 DB — db/work)
--
-- 응급·긴급 요청이 접수되지 않은 채 오래 머물면 한 번 알린다. 알렸다는 표시가 필요하다.
-- 요청의 상태가 아니라 알림 기록이라 version 을 올리지 않고 이 칸만 찍는다.
-- =========================================================

ALTER TABLE work_order ADD COLUMN delay_notified_at TIMESTAMPTZ;

-- 매분 "아직 접수 안 됐고 알리지도 않은 응급·긴급" 을 찾는다. 그 행은 늘 몇 건뿐이다.
CREATE INDEX idx_wo_delay_watch ON work_order (requested_at)
    WHERE status = 'REQUESTED' AND delay_notified_at IS NULL AND priority IN ('EMERGENCY', 'URGENT');
