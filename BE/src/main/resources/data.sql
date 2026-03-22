-- 테스트용 더미 유저 (id=1) 삽입 - 이미 존재하면 무시
INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (1, 'test@test.com', '테스터', 'KAKAO', 'test-kakao-123', 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (2, 'test2@test.com', '테스터2', 'KAKAO', 'test-kakao-124', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (3, 'test3@test.com', '테스터3', 'KAKAO', 'test-kakao-125', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (4, 'test4@test.com', '테스터4', 'KAKAO', 'test-kakao-126', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (5, 'test5@test.com', '테스터5', 'KAKAO', 'test-kakao-127', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (6, 'test6@test.com', '테스터6', 'KAKAO', 'test-kakao-128', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (7, 'test7@test.com', '테스터7', 'KAKAO', 'test-kakao-129', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (8, 'test8@test.com', '테스터8', 'KAKAO', 'test-kakao-130', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (9, 'test9@test.com', '테스터9', 'KAKAO', 'test-kakao-131', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES (10, 'test10@test.com', '테스터10', 'KAKAO', 'test-kakao-132', 0, NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- 시퀀스를 현재 최대 id에 맞게 동기화 (auto increment 충돌 방지)
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1));
