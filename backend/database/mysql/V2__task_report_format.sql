ALTER TABLE BVLDB.task
  ADD COLUMN report_format_id INT NOT NULL DEFAULT 0 AFTER company_id,
  DROP INDEX idx_task_company_order,
  ADD INDEX idx_task_company_format_order(company_id, report_format_id, order_no, id);
