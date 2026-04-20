-- Reset SYOS demo data (safe to re-run before demonstration)
-- Usage: mysql -u syos -p syos_billing < scripts/reseed-database.sql

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE bill_items;
TRUNCATE TABLE transactions;
TRUNCATE TABLE bills;
TRUNCATE TABLE stock_batches;
TRUNCATE TABLE users;
TRUNCATE TABLE items;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO items (item_code, name, unit_price) VALUES
    ('APPLE001', 'Red Apples', 2.50),
    ('BREAD001', 'White Bread', 3.00),
    ('MILK001', 'Fresh Milk', 4.50),
    ('EGG001', 'Chicken Eggs', 5.00),
    ('RICE001', 'Basmati Rice', 8.00);

INSERT INTO stock_batches (batch_id, item_code, purchase_date, expiry_date, quantity, stock_type) VALUES
    ('BATCH-000001', 'APPLE001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 100, 'STORE'),
    ('BATCH-000002', 'BREAD001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 80, 'STORE'),
    ('BATCH-000003', 'MILK001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 60, 'STORE'),
    ('BATCH-000004', 'EGG001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 120, 'STORE'),
    ('BATCH-000005', 'RICE001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 200, 'STORE'),
    ('BATCH-000006', 'MILK001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 50, 'ONLINE'),
    ('BATCH-000007', 'EGG001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 100, 'ONLINE'),
    ('BATCH-000008', 'RICE001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 150, 'ONLINE'),
    ('BATCH-000009', 'APPLE001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 50, 'SHELF'),
    ('BATCH-000010', 'BREAD001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 40, 'SHELF'),
    ('BATCH-000011', 'MILK001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 30, 'SHELF'),
    ('BATCH-000012', 'EGG001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 60, 'SHELF');

INSERT INTO id_sequences (sequence_name, current_value) VALUES
    ('BILL_SERIAL', 0),
    ('BATCH_ID', 12),
    ('USER_ID', 1),
    ('TXN_ID', 0)
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);

SELECT 'items' AS tbl, COUNT(*) AS cnt FROM items
UNION ALL SELECT 'stock_batches', COUNT(*) FROM stock_batches;
