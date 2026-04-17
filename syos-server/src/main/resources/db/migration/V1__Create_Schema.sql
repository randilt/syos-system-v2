CREATE TABLE items (
    item_code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

CREATE TABLE users (
    user_id VARCHAR(20) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL
);

CREATE TABLE stock_batches (
    batch_id VARCHAR(20) PRIMARY KEY,
    item_code VARCHAR(20) NOT NULL,
    purchase_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    quantity INT NOT NULL,
    stock_type VARCHAR(20) NOT NULL,
    CONSTRAINT fk_stock_item
        FOREIGN KEY (item_code) REFERENCES items(item_code)
);

CREATE INDEX idx_stock_item_type ON stock_batches(item_code, stock_type);

CREATE TABLE bills (
    serial_number INT PRIMARY KEY,
    bill_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    full_price DECIMAL(10, 2) NOT NULL,
    discount DECIMAL(10, 2) NOT NULL,
    cash_tendered DECIMAL(10, 2),
    change_amount DECIMAL(10, 2),
    user_id VARCHAR(20)
);

CREATE TABLE bill_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_serial_number INT NOT NULL,
    item_code VARCHAR(20) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_bill_items_bill
        FOREIGN KEY (bill_serial_number) REFERENCES bills(serial_number),
    CONSTRAINT fk_bill_items_item
        FOREIGN KEY (item_code) REFERENCES items(item_code)
);

CREATE TABLE transactions (
    transaction_id VARCHAR(20) PRIMARY KEY,
    bill_serial_number INT NOT NULL,
    txn_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    user_id VARCHAR(20),
    CONSTRAINT fk_transactions_bill
        FOREIGN KEY (bill_serial_number) REFERENCES bills(serial_number)
);
