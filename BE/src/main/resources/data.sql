-- ============================================================
-- 더미 유저 30명 (PLATINUM 1~10, GOLD 11~20, SILVER 21~30)
-- ============================================================
INSERT INTO users (id, email, nickname, provider, provider_id, visited_count, created_at, updated_at)
VALUES
  (1,  'user01@mail.com', '새벽세시공부',   'KAKAO', 'kakao-001', 12, NOW(), NOW()),
  (2,  'user02@mail.com', '영어정복자',     'KAKAO', 'kakao-002', 20, NOW(), NOW()),
  (3,  'user03@mail.com', '발음천재됩니다', 'KAKAO', 'kakao-003', 15, NOW(), NOW()),
  (4,  'user04@mail.com', '쉐도잉달인',     'KAKAO', 'kakao-004', 18, NOW(), NOW()),
  (5,  'user05@mail.com', '스피킹마스터',   'KAKAO', 'kakao-005', 22, NOW(), NOW()),
  (6,  'user06@mail.com', '원어민목표',     'KAKAO', 'kakao-006', 10, NOW(), NOW()),
  (7,  'user07@mail.com', '매일따라읽기',   'KAKAO', 'kakao-007', 25, NOW(), NOW()),
  (8,  'user08@mail.com', '발음교정완료',   'KAKAO', 'kakao-008', 17, NOW(), NOW()),
  (9,  'user09@mail.com', '영어에진심',     'KAKAO', 'kakao-009', 14, NOW(), NOW()),
  (10, 'user10@mail.com', '미드영어공부',   'KAKAO', 'kakao-010', 19, NOW(), NOW()),
  (11, 'user11@mail.com', '따라말하기왕',   'KAKAO', 'kakao-011',  9, NOW(), NOW()),
  (12, 'user12@mail.com', '하루한문장씩',   'KAKAO', 'kakao-012', 11, NOW(), NOW()),
  (13, 'user13@mail.com', '영어왕되는중',   'KAKAO', 'kakao-013',  8, NOW(), NOW()),
  (14, 'user14@mail.com', '발음좋은사람',   'KAKAO', 'kakao-014', 13, NOW(), NOW()),
  (15, 'user15@mail.com', '스피킹연습생',   'KAKAO', 'kakao-015',  7, NOW(), NOW()),
  (16, 'user16@mail.com', '공부하는직장인', 'KAKAO', 'kakao-016', 16, NOW(), NOW()),
  (17, 'user17@mail.com', '영어일기쓰기',   'KAKAO', 'kakao-017',  6, NOW(), NOW()),
  (18, 'user18@mail.com', '원어민처럼말해', 'KAKAO', 'kakao-018', 10, NOW(), NOW()),
  (19, 'user19@mail.com', '쉐도잉입문자',   'KAKAO', 'kakao-019',  5, NOW(), NOW()),
  (20, 'user20@mail.com', '발음개선중',     'KAKAO', 'kakao-020',  8, NOW(), NOW()),
  (21, 'user21@mail.com', '영어초보탈출',   'KAKAO', 'kakao-021',  4, NOW(), NOW()),
  (22, 'user22@mail.com', '따라읽기시작',   'KAKAO', 'kakao-022',  3, NOW(), NOW()),
  (23, 'user23@mail.com', '오늘도한마디',   'KAKAO', 'kakao-023',  5, NOW(), NOW()),
  (24, 'user24@mail.com', '영어공부기록',   'KAKAO', 'kakao-024',  2, NOW(), NOW()),
  (25, 'user25@mail.com', '발음연습중',     'KAKAO', 'kakao-025',  4, NOW(), NOW()),
  (26, 'user26@mail.com', '스피킹도전',     'KAKAO', 'kakao-026',  3, NOW(), NOW()),
  (27, 'user27@mail.com', '영어배우는중',   'KAKAO', 'kakao-027',  2, NOW(), NOW()),
  (28, 'user28@mail.com', '따라하기연습',   'KAKAO', 'kakao-028',  1, NOW(), NOW()),
  (29, 'user29@mail.com', '영어첫걸음',     'KAKAO', 'kakao-029',  2, NOW(), NOW()),
  (30, 'user30@mail.com', '발음입문자',     'KAKAO', 'kakao-030',  1, NOW(), NOW())
ON CONFLICT (id) DO UPDATE
  SET nickname    = EXCLUDED.nickname,
      email       = EXCLUDED.email,
      provider_id = EXCLUDED.provider_id,
      updated_at  = NOW();

SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1));

-- ============================================================
-- 유저 게임 프로필
-- 티어는 리그 내 주간 순위 기반 승급/강등으로 결정되며,
-- weekly_score는 티어 커트라인이 아닌 이번 주 누적 점수.
-- 티어가 다른 유저끼리 weekly_score 역전이 존재하는 것이 정상.
-- ============================================================
INSERT INTO user_game_profiles (user_id, tier, weekly_score, consecutive_no_play_weeks, frozen, last_played_week, created_at, updated_at)
VALUES
  -- PLATINUM (1~10)
  (1,  'PLATINUM', 435.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (2,  'PLATINUM', 398.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (3,  'PLATINUM', 372.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (4,  'PLATINUM', 345.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (5,  'PLATINUM', 321.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (6,  'PLATINUM', 298.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (7,  'PLATINUM', 275.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (8,  'PLATINUM', 252.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (9,  'PLATINUM', 231.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (10, 'PLATINUM', 210.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  -- GOLD (11~20): user11의 weekly_score가 platinum 1위보다 높음 — 리그가 다르므로 정상
  (11, 'GOLD', 468.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (12, 'GOLD', 312.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (13, 'GOLD', 298.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (14, 'GOLD', 225.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (15, 'GOLD', 212.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (16, 'GOLD', 198.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (17, 'GOLD', 185.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (18, 'GOLD', 170.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (19, 'GOLD', 160.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (20, 'GOLD', 152.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  -- SILVER (21~30): user21의 weekly_score가 platinum 2위보다 높음 — 리그가 다르므로 정상
  (21, 'SILVER', 420.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (22, 'SILVER', 360.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (23, 'SILVER', 300.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (24, 'SILVER', 250.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (25, 'SILVER', 200.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (26, 'SILVER', 160.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (27, 'SILVER', 120.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (28, 'SILVER',  90.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (29, 'SILVER',  60.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW()),
  (30, 'SILVER',  30.00, 0, false, date_trunc('week', CURRENT_DATE)::date, NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- 게임 기록 (game_records)
-- final_score = avg_total * (1 + hearts*0.1) * 레벨배수
--   level1=1.0, level2=1.5, level3=2.0
-- avg_total = wordAccuracy*0.5 + wordRhythm*0.3 + dynamicStress*0.2
--
-- 티어와 무관하게 개인 실력에 따라 점수가 결정.
-- GOLD user11, SILVER user21 등이 일부 PLATINUM보다 높은 점수 보유.
--
-- 레벨 해금 조건: 이전 레벨 daily_best hearts >= 1
--   PLATINUM (1~10): level1 hearts>=1 → level2, level2 hearts>=1 → level3 (전원)
--   GOLD (11~17,19,20): level1 hearts>=1 → level2 / user18은 hearts=0으로 level2 없음
--   SILVER (21~30): level1만 플레이
-- ============================================================

-- ── Level 1 (전체 30명) ────────────────────────────────────
INSERT INTO game_records (id, user_id, level, played_date, hearts, avg_total_score, avg_word_rhythm_score, avg_dynamic_stress_score, avg_word_accuracy, final_score, created_at, updated_at)
VALUES
-- PLATINUM: 개인 실력차 있음, hearts 1~3
  (1,  1,  1, CURRENT_DATE, 2, 76.00, 71.50, 69.00, 80.00,  91.20, NOW(), NOW()),
  (2,  2,  1, CURRENT_DATE, 1, 72.50, 68.00, 65.00, 75.50,  79.75, NOW(), NOW()),
  (3,  3,  1, CURRENT_DATE, 3, 85.00, 80.50, 77.00, 89.00, 110.50, NOW(), NOW()),
  (4,  4,  1, CURRENT_DATE, 1, 64.00, 60.00, 57.00, 67.50,  70.40, NOW(), NOW()),
  (5,  5,  1, CURRENT_DATE, 3, 90.00, 85.50, 82.00, 94.00, 117.00, NOW(), NOW()),
  (6,  6,  1, CURRENT_DATE, 2, 68.00, 63.50, 61.00, 71.50,  81.60, NOW(), NOW()),
  (7,  7,  1, CURRENT_DATE, 1, 55.00, 51.50, 49.00, 58.00,  60.50, NOW(), NOW()),
  (8,  8,  1, CURRENT_DATE, 3, 82.00, 77.50, 74.00, 86.00, 106.60, NOW(), NOW()),
  (9,  9,  1, CURRENT_DATE, 2, 74.00, 69.50, 67.00, 77.50,  88.80, NOW(), NOW()),
  (10, 10, 1, CURRENT_DATE, 1, 77.00, 72.50, 69.50, 81.00,  84.70, NOW(), NOW()),
-- GOLD: 티어와 무관, user11은 이번 주 가장 많이 플레이한 high-activity 유저
  (11, 11, 1, CURRENT_DATE, 3, 92.00, 87.50, 84.00, 96.00, 119.60, NOW(), NOW()),
  (12, 12, 1, CURRENT_DATE, 2, 80.00, 75.50, 72.00, 84.00,  96.00, NOW(), NOW()),
  (13, 13, 1, CURRENT_DATE, 3, 75.00, 70.50, 68.00, 78.50,  97.50, NOW(), NOW()),
  (14, 14, 1, CURRENT_DATE, 1, 70.00, 65.50, 63.00, 73.50,  77.00, NOW(), NOW()),
  (15, 15, 1, CURRENT_DATE, 2, 66.00, 61.50, 59.00, 69.50,  79.20, NOW(), NOW()),
  (16, 16, 1, CURRENT_DATE, 1, 61.00, 57.00, 54.00, 64.00,  67.10, NOW(), NOW()),
  (17, 17, 1, CURRENT_DATE, 2, 73.00, 68.50, 65.50, 76.50,  87.60, NOW(), NOW()),
  (18, 18, 1, CURRENT_DATE, 0, 58.00, 54.00, 51.50, 61.00,  58.00, NOW(), NOW()),  -- hearts=0 → level2 접근 불가
  (19, 19, 1, CURRENT_DATE, 1, 60.00, 56.00, 53.50, 63.00,  66.00, NOW(), NOW()),
  (20, 20, 1, CURRENT_DATE, 1, 56.00, 52.50, 50.00, 59.00,  61.60, NOW(), NOW()),
-- SILVER: user21,22 등 high-activity, 점수 자체는 다양
  (21, 21, 1, CURRENT_DATE, 3, 88.00, 83.50, 80.00, 92.00, 114.40, NOW(), NOW()),
  (22, 22, 1, CURRENT_DATE, 2, 84.00, 79.50, 76.00, 88.00, 100.80, NOW(), NOW()),
  (23, 23, 1, CURRENT_DATE, 3, 79.00, 74.50, 71.50, 83.00, 102.70, NOW(), NOW()),
  (24, 24, 1, CURRENT_DATE, 2, 71.00, 66.50, 64.00, 74.50,  85.20, NOW(), NOW()),
  (25, 25, 1, CURRENT_DATE, 1, 67.00, 62.50, 60.00, 70.50,  73.70, NOW(), NOW()),
  (26, 26, 1, CURRENT_DATE, 2, 63.00, 58.50, 56.50, 66.00,  75.60, NOW(), NOW()),
  (27, 27, 1, CURRENT_DATE, 1, 57.00, 53.00, 50.50, 60.00,  62.70, NOW(), NOW()),
  (28, 28, 1, CURRENT_DATE, 0, 52.00, 48.50, 46.00, 55.00,  52.00, NOW(), NOW()),
  (29, 29, 1, CURRENT_DATE, 0, 48.00, 44.50, 42.00, 50.50,  48.00, NOW(), NOW()),
  (30, 30, 1, CURRENT_DATE, 0, 44.00, 40.50, 38.50, 46.00,  44.00, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Level 2 (PLATINUM 전원 + GOLD 중 level1 hearts>=1인 유저) ─
INSERT INTO game_records (id, user_id, level, played_date, hearts, avg_total_score, avg_word_rhythm_score, avg_dynamic_stress_score, avg_word_accuracy, final_score, created_at, updated_at)
VALUES
-- PLATINUM: level2, multiplier=1.5, hearts 1~3
  (31,  1, 2, CURRENT_DATE, 2, 74.00, 69.50, 67.00, 77.50, 133.20, NOW(), NOW()),
  (32,  2, 2, CURRENT_DATE, 1, 70.00, 65.50, 63.00, 73.50, 115.50, NOW(), NOW()),
  (33,  3, 2, CURRENT_DATE, 3, 83.00, 78.50, 75.00, 87.00, 161.85, NOW(), NOW()),
  (34,  4, 2, CURRENT_DATE, 1, 62.00, 57.50, 55.00, 65.50, 102.30, NOW(), NOW()),
  (35,  5, 2, CURRENT_DATE, 3, 88.00, 83.50, 80.00, 92.00, 171.60, NOW(), NOW()),
  (36,  6, 2, CURRENT_DATE, 2, 66.00, 61.50, 59.00, 69.50, 118.80, NOW(), NOW()),
  (37,  7, 2, CURRENT_DATE, 1, 53.00, 49.00, 47.00, 55.50,  87.45, NOW(), NOW()),
  (38,  8, 2, CURRENT_DATE, 3, 80.00, 75.50, 72.00, 84.00, 156.00, NOW(), NOW()),
  (39,  9, 2, CURRENT_DATE, 2, 72.00, 67.50, 65.00, 75.50, 129.60, NOW(), NOW()),
  (40, 10, 2, CURRENT_DATE, 1, 75.00, 70.50, 67.50, 79.00, 123.75, NOW(), NOW()),
-- GOLD: user11~17, 19, 20 (user18 제외 — level1 hearts=0)
  (41, 11, 2, CURRENT_DATE, 3, 90.00, 85.50, 82.00, 94.00, 175.50, NOW(), NOW()),
  (42, 12, 2, CURRENT_DATE, 2, 78.00, 73.50, 70.50, 82.00, 140.40, NOW(), NOW()),
  (43, 13, 2, CURRENT_DATE, 3, 73.00, 68.50, 66.00, 76.50, 142.35, NOW(), NOW()),
  (44, 14, 2, CURRENT_DATE, 1, 68.00, 63.50, 61.00, 71.50, 112.20, NOW(), NOW()),
  (45, 15, 2, CURRENT_DATE, 2, 64.00, 59.50, 57.00, 67.50, 115.20, NOW(), NOW()),
  (46, 16, 2, CURRENT_DATE, 1, 59.00, 54.50, 52.00, 62.00,  97.35, NOW(), NOW()),
  (47, 17, 2, CURRENT_DATE, 2, 70.00, 65.50, 63.00, 73.50, 126.00, NOW(), NOW()),
  (48, 19, 2, CURRENT_DATE, 1, 58.00, 53.50, 51.00, 61.00,  95.70, NOW(), NOW()),
  (49, 20, 2, CURRENT_DATE, 0, 54.00, 50.00, 47.50, 57.00,  81.00, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Level 3 (PLATINUM 전원 — level2 hearts>=1 확인됨) ──────
INSERT INTO game_records (id, user_id, level, played_date, hearts, avg_total_score, avg_word_rhythm_score, avg_dynamic_stress_score, avg_word_accuracy, final_score, created_at, updated_at)
VALUES
-- multiplier=2.0, hearts 1~3
  (50,  1, 3, CURRENT_DATE, 2, 73.00, 68.50, 66.00, 76.50, 175.20, NOW(), NOW()),
  (51,  2, 3, CURRENT_DATE, 1, 68.00, 63.50, 61.00, 71.50, 149.60, NOW(), NOW()),
  (52,  3, 3, CURRENT_DATE, 3, 82.00, 77.50, 74.00, 86.00, 213.20, NOW(), NOW()),
  (53,  4, 3, CURRENT_DATE, 1, 60.00, 55.50, 53.00, 63.00, 132.00, NOW(), NOW()),
  (54,  5, 3, CURRENT_DATE, 3, 86.00, 81.50, 78.00, 90.00, 223.60, NOW(), NOW()),
  (55,  6, 3, CURRENT_DATE, 2, 64.50, 60.00, 57.50, 68.00, 154.80, NOW(), NOW()),
  (56,  7, 3, CURRENT_DATE, 1, 51.50, 47.50, 45.00, 54.00, 113.30, NOW(), NOW()),
  (57,  8, 3, CURRENT_DATE, 2, 79.00, 74.50, 71.50, 83.00, 189.60, NOW(), NOW()),
  (58,  9, 3, CURRENT_DATE, 1, 70.00, 65.50, 63.00, 73.50, 154.00, NOW(), NOW()),
  (59, 10, 3, CURRENT_DATE, 2, 73.50, 69.00, 66.50, 77.00, 176.40, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('game_records', 'id'), GREATEST((SELECT MAX(id) FROM game_records), 1));

-- ============================================================
-- 일별 최고 기록 (daily_best_records)
-- PK: (user_id, level, record_date)
-- ============================================================

-- Level 1 (전체 30명)
INSERT INTO daily_best_records (user_id, level, record_date, game_record_id, best_final_score)
VALUES
  (1,  1, CURRENT_DATE,  1,  91.20),
  (2,  1, CURRENT_DATE,  2,  79.75),
  (3,  1, CURRENT_DATE,  3, 110.50),
  (4,  1, CURRENT_DATE,  4,  70.40),
  (5,  1, CURRENT_DATE,  5, 117.00),
  (6,  1, CURRENT_DATE,  6,  81.60),
  (7,  1, CURRENT_DATE,  7,  60.50),
  (8,  1, CURRENT_DATE,  8, 106.60),
  (9,  1, CURRENT_DATE,  9,  88.80),
  (10, 1, CURRENT_DATE, 10,  84.70),
  (11, 1, CURRENT_DATE, 11, 119.60),
  (12, 1, CURRENT_DATE, 12,  96.00),
  (13, 1, CURRENT_DATE, 13,  97.50),
  (14, 1, CURRENT_DATE, 14,  77.00),
  (15, 1, CURRENT_DATE, 15,  79.20),
  (16, 1, CURRENT_DATE, 16,  67.10),
  (17, 1, CURRENT_DATE, 17,  87.60),
  (18, 1, CURRENT_DATE, 18,  58.00),
  (19, 1, CURRENT_DATE, 19,  66.00),
  (20, 1, CURRENT_DATE, 20,  61.60),
  (21, 1, CURRENT_DATE, 21, 114.40),
  (22, 1, CURRENT_DATE, 22, 100.80),
  (23, 1, CURRENT_DATE, 23, 102.70),
  (24, 1, CURRENT_DATE, 24,  85.20),
  (25, 1, CURRENT_DATE, 25,  73.70),
  (26, 1, CURRENT_DATE, 26,  75.60),
  (27, 1, CURRENT_DATE, 27,  62.70),
  (28, 1, CURRENT_DATE, 28,  52.00),
  (29, 1, CURRENT_DATE, 29,  48.00),
  (30, 1, CURRENT_DATE, 30,  44.00)
ON CONFLICT (user_id, level, record_date) DO NOTHING;

-- Level 2 (PLATINUM 1~10, GOLD 11~17, 19, 20)
INSERT INTO daily_best_records (user_id, level, record_date, game_record_id, best_final_score)
VALUES
  (1,  2, CURRENT_DATE, 31, 133.20),
  (2,  2, CURRENT_DATE, 32, 115.50),
  (3,  2, CURRENT_DATE, 33, 161.85),
  (4,  2, CURRENT_DATE, 34, 102.30),
  (5,  2, CURRENT_DATE, 35, 171.60),
  (6,  2, CURRENT_DATE, 36, 118.80),
  (7,  2, CURRENT_DATE, 37,  87.45),
  (8,  2, CURRENT_DATE, 38, 156.00),
  (9,  2, CURRENT_DATE, 39, 129.60),
  (10, 2, CURRENT_DATE, 40, 123.75),
  (11, 2, CURRENT_DATE, 41, 175.50),
  (12, 2, CURRENT_DATE, 42, 140.40),
  (13, 2, CURRENT_DATE, 43, 142.35),
  (14, 2, CURRENT_DATE, 44, 112.20),
  (15, 2, CURRENT_DATE, 45, 115.20),
  (16, 2, CURRENT_DATE, 46,  97.35),
  (17, 2, CURRENT_DATE, 47, 126.00),
  (19, 2, CURRENT_DATE, 48,  95.70),
  (20, 2, CURRENT_DATE, 49,  81.00)
ON CONFLICT (user_id, level, record_date) DO NOTHING;

-- Level 3 (PLATINUM 1~10)
INSERT INTO daily_best_records (user_id, level, record_date, game_record_id, best_final_score)
VALUES
  (1,  3, CURRENT_DATE, 50, 175.20),
  (2,  3, CURRENT_DATE, 51, 149.60),
  (3,  3, CURRENT_DATE, 52, 213.20),
  (4,  3, CURRENT_DATE, 53, 132.00),
  (5,  3, CURRENT_DATE, 54, 223.60),
  (6,  3, CURRENT_DATE, 55, 154.80),
  (7,  3, CURRENT_DATE, 56, 113.30),
  (8,  3, CURRENT_DATE, 57, 189.60),
  (9,  3, CURRENT_DATE, 58, 154.00),
  (10, 3, CURRENT_DATE, 59, 176.40)
ON CONFLICT (user_id, level, record_date) DO NOTHING;
