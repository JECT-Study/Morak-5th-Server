ALTER TABLE participation
    ADD COLUMN invalid_confirmed_at TIMESTAMP(6);

COMMENT ON COLUMN participation.invalid_confirmed_at IS '방장이 무효(INVALIDATED) 신청을 확인한 시각 (UTC). 확인 전에는 NULL';
