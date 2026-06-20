CREATE TABLE IF NOT EXISTS process_definition (
  id VARCHAR(255) NOT NULL,
  version INT NOT NULL,
  name VARCHAR(255),
  nodes_json MEDIUMTEXT,
  transitions_json MEDIUMTEXT,
  PRIMARY KEY (id, version)
);

CREATE TABLE IF NOT EXISTS process_instance (
  id VARCHAR(36) PRIMARY KEY,
  definition_id VARCHAR(255) NOT NULL,
  definition_version INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  variables_json MEDIUMTEXT,
  active_node_ids_json TEXT,
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
  candidate_groups_json TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  variables_json MEDIUMTEXT,
  created_at BIGINT NOT NULL,
  completed_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS draft (
  id VARCHAR(8) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  nodes_json MEDIUMTEXT,
  edges_json MEDIUMTEXT,
  version INT DEFAULT 1,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS definition (
  id VARCHAR(255) NOT NULL,
  version INT NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(255),
  positions_json TEXT,
  PRIMARY KEY (user_id, id, version)
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
  comment TEXT NULL
);
