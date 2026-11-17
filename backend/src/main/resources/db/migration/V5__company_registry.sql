CREATE TABLE company (
 id BIGINT PRIMARY KEY,
 company_name VARCHAR(120) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO company (id, company_name)
SELECT DISTINCT t.company_id, CONCAT('Company ', t.company_id)
FROM task t
WHERE NOT EXISTS (SELECT 1 FROM company c WHERE c.id = t.company_id);

INSERT INTO company (id, company_name)
SELECT 2105259, 'Walmart Canada'
WHERE NOT EXISTS (SELECT 1 FROM company WHERE id = 2105259);

INSERT INTO company (id, company_name)
SELECT 2004550, 'Carter''s'
WHERE NOT EXISTS (SELECT 1 FROM company WHERE id = 2004550);

UPDATE company SET company_name = 'Walmart Canada', enabled = TRUE WHERE id = 2105259;
UPDATE company SET company_name = 'Carter''s', enabled = TRUE WHERE id = 2004550;

ALTER TABLE task ADD CONSTRAINT fk_task_company FOREIGN KEY (company_id) REFERENCES company(id);
CREATE INDEX idx_company_enabled_name ON company(enabled, company_name);
