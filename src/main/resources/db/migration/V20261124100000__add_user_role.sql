ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

COMMENT ON COLUMN users.role IS '권한: USER(일반) / ADMIN(운영진). 운영진 검수 화면(/admin)과 /admin/** API는 ADMIN만 쓴다';
