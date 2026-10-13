CREATE TABLE app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(40) NOT NULL UNIQUE,
 display_name VARCHAR(80) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(20) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 must_change BOOLEAN NOT NULL DEFAULT TRUE, auth_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE brd_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(160) NOT NULL,
 project_name VARCHAR(120) NOT NULL, delivery_batch VARCHAR(80) NOT NULL,
 owner_id BIGINT NOT NULL, current_version_id BIGINT, latest_version_id BIGINT,
 next_sequence INT NOT NULL DEFAULT 1, revision BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(owner_id) REFERENCES app_user(id)
);
CREATE TABLE brd_version (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL,
 sequence_no INT NOT NULL, base_version_id BIGINT,
 object_key VARCHAR(80) NOT NULL UNIQUE, original_filename VARCHAR(255) NOT NULL,
 extension VARCHAR(8) NOT NULL, sha256 VARCHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL, change_note VARCHAR(500) NOT NULL,
 uploaded_by BIGINT NOT NULL, uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(document_id, sequence_no), UNIQUE(document_id, sha256),
 FOREIGN KEY(document_id) REFERENCES brd_document(id),
 FOREIGN KEY(base_version_id) REFERENCES brd_version(id),
 FOREIGN KEY(uploaded_by) REFERENCES app_user(id)
);
ALTER TABLE brd_document ADD CONSTRAINT fk_current FOREIGN KEY(current_version_id) REFERENCES brd_version(id);
ALTER TABLE brd_document ADD CONSTRAINT fk_latest FOREIGN KEY(latest_version_id) REFERENCES brd_version(id);
CREATE TABLE upload_job (
 id VARCHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL, request_key VARCHAR(80) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, document_id BIGINT, base_version_id BIGINT,
 title VARCHAR(160), project_name VARCHAR(120), delivery_batch VARCHAR(80),
 change_note VARCHAR(500) NOT NULL, original_filename VARCHAR(255) NOT NULL,
 extension VARCHAR(8) NOT NULL, sha256 VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL,
 temp_key VARCHAR(80) NOT NULL, state VARCHAR(20) NOT NULL,
 error_code VARCHAR(80), result_version_id BIGINT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(actor_id, request_key), FOREIGN KEY(actor_id) REFERENCES app_user(id)
);
CREATE TABLE operation_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL,
 action VARCHAR(50) NOT NULL, document_id BIGINT, version_id BIGINT,
 from_version_id BIGINT, reason VARCHAR(500),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_document_updated ON brd_document(updated_at);
CREATE INDEX idx_operation_document ON operation_log(document_id, id);
