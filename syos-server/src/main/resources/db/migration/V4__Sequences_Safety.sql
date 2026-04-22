-- InnoDB is required for FOR UPDATE row locking used by JdbcSequenceGenerator.
-- MyISAM does not support row-level locks, which can break sequence safety.
ALTER TABLE id_sequences ENGINE=InnoDB;
