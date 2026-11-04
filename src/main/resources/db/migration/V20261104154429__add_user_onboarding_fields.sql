ALTER TABLE users
    ADD COLUMN bio VARCHAR(255),
    ADD COLUMN profile_link_url VARCHAR(255);

CREATE TABLE user_interest (
    user_id       BIGINT      NOT NULL,
    interest_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, interest_code),
    CONSTRAINT fk_user_interest_user FOREIGN KEY (user_id) REFERENCES users (id)
);

COMMENT ON COLUMN users.bio IS '프로필 한 줄 소개';
COMMENT ON COLUMN users.profile_link_url IS '프로필 링크 URL';
COMMENT ON TABLE user_interest IS '유저가 선택한 관심 분야';
COMMENT ON COLUMN user_interest.interest_code IS '온보딩 관심 분야 코드';
