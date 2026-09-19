-- =========================================================
-- V20__add_hold_by_side.sql  (업무 DB — db/work)
--
-- 보류를 건 쪽. 푸는 것도 이 쪽만 할 수 있다.
-- 사람이 아니라 파트(REQUESTER / PERFORMER)다. 건 사람이 퇴근해도 다음 교대 근무자가 풀어야 한다.
-- 상대 파트가 풀 수 있으면 "지금은 진행하지 말자" 고 멈춰 세운 판단을 상대가 되돌리게 된다.
-- =========================================================

ALTER TABLE work_order ADD COLUMN hold_by_side VARCHAR(10);

COMMENT ON COLUMN work_order.hold_by_side IS
'보류를 건 쪽 (REQUESTER=요청 파트 / PERFORMER=수행 파트). 보류 해제는 이 쪽만 할 수 있다. 보류가 아니면 NULL';

-- 지금 보류 중인 요청은 이력에서 누가 걸었는지 되짚어 채운다.
-- 마지막 ON_HOLD 전이의 행위자 파트가 요청 파트면 REQUESTER, 아니면 PERFORMER 다.
UPDATE work_order w
   SET hold_by_side = CASE WHEN e.actor_dept_id = w.from_department_id THEN 'REQUESTER' ELSE 'PERFORMER' END
  FROM (
        SELECT DISTINCT ON (order_id) order_id, actor_dept_id
          FROM work_order_event
         WHERE to_status = 'ON_HOLD'
         ORDER BY order_id, occurred_at DESC, id DESC
       ) e
 WHERE e.order_id = w.id
   AND w.status = 'ON_HOLD';

-- 보류면 반드시 누가 걸었는지가 있고, 보류가 아니면 없다. hold_from_status 와 같은 규칙이다.
ALTER TABLE work_order ADD CONSTRAINT ck_wo_hold_by_side
    CHECK ((status = 'ON_HOLD') = (hold_by_side IS NOT NULL));
