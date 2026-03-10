-- 테스트용 더미 유저 (id=1) 삽입 - 이미 존재하면 무시
INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (1, 'test@test.com', '테스터', 'KAKAO', 'test-kakao-123', 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 시퀀스를 현재 최대 id에 맞게 동기화 (auto increment 충돌 방지)
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1));
