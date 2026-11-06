-- 채팅 전송 지원: 시스템 메시지(participation_id NULL), 재전송 중복 방지, 인증 스토리 답글
ALTER TABLE chat_message ALTER COLUMN participation_id DROP NOT NULL;
ALTER TABLE chat_message ADD COLUMN message_type VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE chat_message ADD COLUMN client_message_id UUID;
ALTER TABLE chat_message ADD COLUMN reply_certification_id BIGINT;

-- 같은 참가자가 같은 client_message_id로 보낸 메시지는 한 개만 저장한다 (NULL은 중복으로 보지 않는다)
CREATE UNIQUE INDEX uq_chat_message_participation_client
    ON chat_message (participation_id, client_message_id);

-- 채팅방 메시지 조회(최신순/커서) 인덱스
CREATE INDEX idx_chat_message_challenge_id ON chat_message (challenge_id, id);
