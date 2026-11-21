ALTER TABLE challenge
    ADD COLUMN start_held_on DATE;

COMMENT ON COLUMN challenge.start_held_on IS '시작 보류(START_HELD)로 바뀐 날 (KST 날짜). 이 날 다음 00:00부터 인원이 차면 시작한다. 보류된 적이 없거나 기간을 재설정하면 NULL';
