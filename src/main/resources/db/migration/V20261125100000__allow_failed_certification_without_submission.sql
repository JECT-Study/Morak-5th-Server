-- 00:00 배치가 만드는 미제출 FAILED 행은 제출 시각이 없다(위키 §4.4). 제출된 인증은 계속 제출 시각이 있어야 한다.
ALTER TABLE certification ALTER COLUMN submitted_at DROP NOT NULL;

ALTER TABLE certification
    ADD CONSTRAINT ck_certification_submitted_at CHECK (submitted_at IS NOT NULL OR status = 'FAILED');

COMMENT ON COLUMN certification.submitted_at IS '제출 시각 (UTC). 미제출로 만든 FAILED 행은 NULL';
COMMENT ON COLUMN certification.status IS '상태: PENDING(판정 대기) / APPROVED(승인) / AUTO_APPROVED(정오 자동 승인) / REJECTED(거절) / FAILED(미제출·마감 후 제출)';
COMMENT ON COLUMN certification.judge_due_at IS '판정 마감 시각 (UTC). 포인트 없는 챌린지는 인증 다음 날 12:00 KST, 포인트 챌린지(운영진 검수)는 NULL';
