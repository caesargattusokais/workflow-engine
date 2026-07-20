-- H2-compatible schema for JDBC repository tests
-- Replaces MySQL-specific MEDIUMTEXT with CLOB

CREATE TABLE IF NOT EXISTS process_definition (
  id VARCHAR(255) NOT NULL,
  version INT NOT NULL,
  name VARCHAR(255),
  nodes_json CLOB,
  transitions_json CLOB,
  PRIMARY KEY (id, version)
);

CREATE TABLE IF NOT EXISTS process_instance (
  id VARCHAR(36) PRIMARY KEY,
  definition_id VARCHAR(255) NOT NULL,
  definition_version INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  variables_json CLOB,
  active_node_ids_json CLOB,
  parent_instance_id VARCHAR(64) NULL,
  parent_execution_id VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  completed_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS execution (
  id VARCHAR(36) PRIMARY KEY,
  instance_id VARCHAR(36) NOT NULL,
  current_node_id VARCHAR(255) NOT NULL,
  parent_execution_id VARCHAR(36) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  retry_attempt INT DEFAULT 0,
  next_retry_at BIGINT DEFAULT 0,
  retry_state VARCHAR(50) NULL
);

CREATE TABLE IF NOT EXISTS task (
  id VARCHAR(36) PRIMARY KEY,
  instance_id VARCHAR(36) NOT NULL,
  node_id VARCHAR(255) NOT NULL,
  assignee VARCHAR(255) NULL,
  candidate_groups_json CLOB,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  variables_json CLOB,
  created_at BIGINT NOT NULL,
  completed_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS historic_activity (
  id VARCHAR(36) PRIMARY KEY,
  instance_id VARCHAR(36) NOT NULL,
  node_id VARCHAR(255) NOT NULL,
  node_name VARCHAR(255) NULL,
  node_type VARCHAR(50) NULL,
  executor VARCHAR(255) DEFAULT 'system',
  action VARCHAR(50) NOT NULL,
  timestamp BIGINT NOT NULL,
  comment CLOB NULL
);
