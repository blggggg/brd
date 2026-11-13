ALTER TABLE task ADD COLUMN report_format_id INT NOT NULL DEFAULT 0;
DROP INDEX idx_task_company_order;
CREATE INDEX idx_task_company_format_order ON task(company_id, report_format_id, order_no, id);
