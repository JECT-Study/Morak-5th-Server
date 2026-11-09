ALTER TABLE challenge
    ADD COLUMN purpose TEXT,
    ADD COLUMN common_mission TEXT,
    ADD COLUMN verification_rules TEXT,
    ADD COLUMN expectations TEXT,
    ALTER COLUMN duration_weeks DROP NOT NULL;

COMMENT ON COLUMN challenge.duration_weeks IS '기존 데이터용. 신규 챌린지의 일수는 시작일과 종료일로 계산';
