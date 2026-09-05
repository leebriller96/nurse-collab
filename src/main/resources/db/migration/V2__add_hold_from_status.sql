ALTER TABLE transfer_request
    ADD COLUMN hold_from_status VARCHAR(20);

COMMENT ON COLUMN transfer_request.hold_from_status IS
'ON_HOLD 진입 직전 상태. 보류 해제 시 이 상태로 복귀한다';
