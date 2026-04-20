#!/bin/bash
# Reload catalogue + stock for demos (Flyway V2 only runs once on first migrate).
cd "$(dirname "$0")/.."
mysql -u syos -psyos -h127.0.0.1 syos_billing < scripts/reseed-database.sql
