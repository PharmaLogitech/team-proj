-- Fix: "Data truncated for column 'status'" when placing orders (MySQL).
--
-- Older schemas may have `orders.status` as ENUM(...) that does not list
-- ACCEPTED, PROCESSING, DISPATCHED, etc. MySQL rejects INSERT/UPDATE with
-- values not in the ENUM definition. Hibernate ddl-auto=update often does
-- not expand ENUM columns.
--
-- Run once against your ipos_sa database (adjust schema name if needed):

USE ipos_sa;

-- Migrate legacy values before widening the column (safe if already VARCHAR)
UPDATE orders SET status = 'ACCEPTED' WHERE status IN ('PENDING', 'CONFIRMED');

-- Replace ENUM with VARCHAR so all OrderStatus string values are accepted
ALTER TABLE orders
  MODIFY COLUMN status VARCHAR(32) NOT NULL;
