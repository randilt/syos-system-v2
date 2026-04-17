INSERT INTO items (item_code, name, unit_price) VALUES
    ('APPLE001', 'Red Apples', 2.50),
    ('BREAD001', 'White Bread', 3.00),
    ('MILK001', 'Fresh Milk', 4.50),
    ('EGG001', 'Chicken Eggs', 5.00),
    ('RICE001', 'Basmati Rice', 8.00);

INSERT INTO stock_batches (batch_id, item_code, purchase_date, expiry_date, quantity, stock_type) VALUES
    ('BATCH-000001', 'APPLE001', '2026-02-01', '2026-02-08', 100, 'STORE'),
    ('BATCH-000002', 'BREAD001', '2026-02-01', '2026-02-08', 80, 'STORE'),
    ('BATCH-000003', 'MILK001', '2026-02-01', '2026-02-08', 60, 'STORE'),
    ('BATCH-000004', 'EGG001', '2026-02-01', '2026-03-01', 120, 'STORE'),
    ('BATCH-000005', 'RICE001', '2026-02-01', '2026-03-01', 200, 'STORE'),
    ('BATCH-000006', 'MILK001', '2026-02-01', '2026-02-08', 50, 'ONLINE'),
    ('BATCH-000007', 'EGG001', '2026-02-01', '2026-03-01', 100, 'ONLINE'),
    ('BATCH-000008', 'RICE001', '2026-02-01', '2026-03-01', 150, 'ONLINE'),
    ('BATCH-000009', 'APPLE001', '2026-02-01', '2026-02-08', 50, 'SHELF'),
    ('BATCH-000010', 'BREAD001', '2026-02-01', '2026-02-08', 40, 'SHELF'),
    ('BATCH-000011', 'MILK001', '2026-02-01', '2026-02-08', 30, 'SHELF'),
    ('BATCH-000012', 'EGG001', '2026-02-01', '2026-03-01', 60, 'SHELF');
