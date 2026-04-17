CREATE TABLE id_sequences (
    sequence_name VARCHAR(50) PRIMARY KEY,
    current_value INT NOT NULL
);

INSERT INTO id_sequences (sequence_name, current_value) VALUES
('BILL_SERIAL', 0),
('BATCH_ID', 12),
('USER_ID', 1),
('TXN_ID', 0);
