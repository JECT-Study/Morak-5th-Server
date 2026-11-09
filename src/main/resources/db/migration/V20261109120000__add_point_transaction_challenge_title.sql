-- 거래 내역에 챌린지 이름을 표시하기 위해 거래 당시 이름을 저장한다 (보증금 납부·환급만, 지급은 NULL)
ALTER TABLE point_transaction ADD COLUMN challenge_title VARCHAR(50);

COMMENT ON COLUMN point_transaction.challenge_title IS '거래 당시 챌린지 이름 (DEPOSIT/REFUND일 때, 이후 이름이 바뀌어도 갱신하지 않음)';
