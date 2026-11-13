-- 위키 §5 기준: 참가자의 포인트 상태는 point_status(NONE / HELD / REFUNDED / SETTLED)다.
ALTER TABLE participation RENAME COLUMN deposit_status TO point_status;

COMMENT ON COLUMN participation.point_status IS '포인트 상태: NONE(없음) / HELD(걸림) / REFUNDED(반환) / SETTLED(정산)';
