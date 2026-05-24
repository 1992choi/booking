SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

USE db_api;

-- =============================================
-- 1. 유저 생성
--    password: 12341234 (BCrypt)
-- =============================================
INSERT INTO users (name, email, phone, password, role, created_at, updated_at)
VALUES ('관리자', 'admin@bookit.com', '010-1111-1111', '$2a$10$k6nl/zUrrsYBBDgclMglKetDayaPCEwg5voMQI3uRzBrOA2vuhLji', 'ADMIN', NOW(), NOW()),
       ('테스터', 'test@bookit.com', '010-2222-2222', '$2a$10$k6nl/zUrrsYBBDgclMglKetDayaPCEwg5voMQI3uRzBrOA2vuhLji', 'USER', NOW(), NOW()),
       ('한강뷰펜션', 'pension@bookit.com', '010-4444-4444', '$2a$10$k6nl/zUrrsYBBDgclMglKetDayaPCEwg5voMQI3uRzBrOA2vuhLji', 'MERCHANT', NOW(), NOW()),
       ('서울쿠킹클래스', 'class@bookit.com', '010-5555-5555', '$2a$10$k6nl/zUrrsYBBDgclMglKetDayaPCEwg5voMQI3uRzBrOA2vuhLji', 'MERCHANT', NOW(), NOW()),
       ('강남피트니스', 'facility@bookit.com', '010-6666-6666', '$2a$10$k6nl/zUrrsYBBDgclMglKetDayaPCEwg5voMQI3uRzBrOA2vuhLji', 'MERCHANT', NOW(), NOW());

USE db_reservation;

-- =============================================
-- 2. Users 동기화 (db_api.users → db_reservation.users)
-- =============================================
INSERT INTO users (id, name, email, created_at, updated_at)
SELECT id, name, email, created_at, updated_at FROM db_api.users;

USE db_payment;

-- =============================================
-- Users 동기화 (db_api.users → db_payment.users)
-- =============================================
INSERT INTO users (id, name, email, phone, created_at, updated_at)
SELECT id, name, email, phone, created_at, updated_at FROM db_api.users;

USE db_notification;

-- =============================================
-- Users 동기화 (db_api.users → db_notification.users)
-- =============================================
INSERT INTO users (id, name, email, phone, created_at, updated_at)
SELECT id, name, email, phone, created_at, updated_at FROM db_api.users;

USE db_reservation;

-- =============================================
-- 3. Merchant 3개 생성 (PENSION / CLASS / FACILITY)
-- =============================================
INSERT INTO merchants (user_id, name, phone, type, created_at, updated_at)
SELECT id, name, phone, 'PENSION', NOW(), NOW()
FROM db_api.users
WHERE email = 'pension@bookit.com';

INSERT INTO merchants (user_id, name, phone, type, created_at, updated_at)
SELECT id, name, phone, 'CLASS', NOW(), NOW()
FROM db_api.users
WHERE email = 'class@bookit.com';

INSERT INTO merchants (user_id, name, phone, type, created_at, updated_at)
SELECT id, name, phone, 'FACILITY', NOW(), NOW()
FROM db_api.users
WHERE email = 'facility@bookit.com';

-- =============================================
-- 3. 펜션 리소스 (한강뷰펜션)
-- =============================================
INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, '한강뷰 A동', '한강이 보이는 넓은 객실입니다. 최대 4인 입실 가능하며 바베큐 시설이 포함되어 있습니다.', 150000, 4, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'pension@bookit.com';

INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, '한강뷰 B동', '테라스가 딸린 프리미엄 객실입니다. 최대 6인 입실 가능하며 한강 야경을 즐기실 수 있습니다.', 200000, 6, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'pension@bookit.com';

-- =============================================
-- 4. 클래스 리소스 (서울쿠킹클래스)
-- =============================================
INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, '이탈리안 파스타 클래스', '직접 파스타 반죽부터 소스까지 만들어보는 2시간 쿠킹 클래스입니다. 재료는 모두 제공됩니다.', 50000, 8, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'class@bookit.com';

INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, '제과제빵 베이킹 클래스', '마카롱과 케이크를 만드는 3시간 베이킹 클래스입니다. 만든 결과물은 가져가실 수 있습니다.', 65000, 6, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'class@bookit.com';

-- =============================================
-- 5. 시설 리소스 (강남피트니스)
-- =============================================
INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, '헬스장 1일 이용권', '최신 운동 기구가 갖춰진 헬스장입니다. 샤워 시설 포함, 오전 6시~밤 11시 이용 가능합니다.', 15000, 50, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'facility@bookit.com';

INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at)
SELECT m.id, 'PT 1회 세션 (60분)', '전문 트레이너와 함께하는 1:1 퍼스널 트레이닝입니다. 체형 분석 및 맞춤 운동 프로그램 제공.', 80000, 1, NOW(), NOW()
FROM merchants m
         JOIN db_api.users u ON m.user_id = u.id
WHERE u.email = 'facility@bookit.com';

-- =============================================
-- 6. 예약 가능 시간 (오늘 기준 향후 5일치)
-- =============================================

-- 한강뷰 A동: 체크인 15:00~익일 11:00 (1박)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id,
       DATE_ADD(CURDATE(), INTERVAL n DAY) + INTERVAL 15 HOUR, DATE_ADD(CURDATE(), INTERVAL n+1 DAY) + INTERVAL 11 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) days
WHERE u.email = 'pension@bookit.com' AND r.name = '한강뷰 A동';

-- 한강뷰 B동: 체크인 15:00~익일 11:00 (1박)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id,
       DATE_ADD(CURDATE(), INTERVAL n DAY) + INTERVAL 15 HOUR, DATE_ADD(CURDATE(), INTERVAL n+1 DAY) + INTERVAL 11 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) days
WHERE u.email = 'pension@bookit.com' AND r.name = '한강뷰 B동';

-- 이탈리안 파스타 클래스: 오전 10시 (2시간), 오후 2시 (2시간)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id, slot_start, slot_start + INTERVAL 2 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 10 HOUR AS slot_start UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 14 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 10 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 14 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 10 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 14 HOUR
    ) slots
WHERE u.email = 'class@bookit.com' AND r.name = '이탈리안 파스타 클래스';

-- 제과제빵 베이킹 클래스: 오전 11시 (3시간), 오후 3시 (3시간)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id, slot_start, slot_start + INTERVAL 3 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 11 HOUR AS slot_start UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 15 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 11 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 15 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 11 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 15 HOUR
    ) slots
WHERE u.email = 'class@bookit.com' AND r.name = '제과제빵 베이킹 클래스';

-- 헬스장 1일 이용권: 오전 9시~오후 9시 (12시간)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id,
       DATE_ADD(CURDATE(), INTERVAL n DAY) + INTERVAL 9 HOUR, DATE_ADD(CURDATE(), INTERVAL n DAY) + INTERVAL 21 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) days
WHERE u.email = 'facility@bookit.com' AND r.name = '헬스장 1일 이용권';

-- PT 1회 세션: 오전 10시, 오후 1시, 오후 4시 (각 60분)
INSERT INTO available_times (resource_id, start_time, end_time, status, created_at, updated_at)
SELECT r.id, slot_start, slot_start + INTERVAL 1 HOUR, 'OPEN', NOW(), NOW()
FROM resources r
    JOIN merchants m ON r.merchant_id = m.id
    JOIN db_api.users u ON m.user_id = u.id
    CROSS JOIN (
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 10 HOUR AS slot_start UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 13 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 0 DAY) + INTERVAL 16 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 10 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 13 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 16 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 10 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 13 HOUR UNION ALL
    SELECT DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 16 HOUR
    ) slots
WHERE u.email = 'facility@bookit.com' AND r.name = 'PT 1회 세션 (60분)';
