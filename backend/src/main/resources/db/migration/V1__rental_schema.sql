-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE customer (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL,
  contact varchar(200) NOT NULL,
  notes varchar(1000) NOT NULL,
  department_id bigint NOT NULL,
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE category (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  name_en varchar(120) NOT NULL
);

CREATE TABLE asset (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  category_id bigint NOT NULL,
  department_id bigint NOT NULL,
  daily_rate decimal(14,2) NOT NULL,
  deposit decimal(14,2) NOT NULL,
  accessories varchar(1000) NOT NULL,
  maintenance boolean NOT NULL,
  ready_at timestamp(6) NULL,
  maintenance_note varchar(1000) NOT NULL,
  FOREIGN KEY (category_id) REFERENCES category(id),
  FOREIGN KEY (department_id) REFERENCES department(id),
  CHECK (daily_rate >= 0 AND deposit >= 0)
);

CREATE TABLE booking (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  number varchar(60) NOT NULL UNIQUE,
  customer_id bigint NOT NULL,
  department_id bigint NOT NULL,
  start_at timestamp(6) NOT NULL,
  end_at timestamp(6) NOT NULL,
  status varchar(20) NOT NULL,
  currency varchar(3) NOT NULL,
  turnaround_hours int NOT NULL,
  notes varchar(1000) NOT NULL,
  created_at timestamp(6) NOT NULL,
  created_by varchar(60) NOT NULL,
  FOREIGN KEY (customer_id) REFERENCES customer(id),
  FOREIGN KEY (department_id) REFERENCES department(id),
  CHECK (end_at > start_at)
);

CREATE TABLE booking_line (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  booking_id bigint NOT NULL,
  asset_id bigint NOT NULL,
  asset_code varchar(60) NOT NULL,
  asset_name varchar(120) NOT NULL,
  accessories varchar(1000) NOT NULL,
  daily_rate decimal(14,2) NOT NULL,
  deposit decimal(14,2) NOT NULL,
  returned_at timestamp(6) NULL,
  late_fee decimal(14,2) NOT NULL,
  damage_fee decimal(14,2) NOT NULL,
  inspection varchar(1000) NOT NULL,
  FOREIGN KEY (booking_id) REFERENCES booking(id),
  FOREIGN KEY (asset_id) REFERENCES asset(id),
  UNIQUE (booking_id, asset_id)
);

CREATE TABLE ledger (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  booking_id bigint NOT NULL,
  kind varchar(30) NOT NULL,
  amount decimal(14,2) NOT NULL,
  reference varchar(120) NOT NULL,
  note varchar(500) NOT NULL,
  created_at timestamp(6) NOT NULL,
  created_by varchar(60) NOT NULL,
  FOREIGN KEY (booking_id) REFERENCES booking(id),
  CHECK (amount > 0)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);

CREATE TABLE mutation_key (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  booking_id bigint NOT NULL,
  request_key varchar(80) NOT NULL,
  action varchar(120) NOT NULL,
  fingerprint varchar(64) NOT NULL,
  FOREIGN KEY (booking_id) REFERENCES booking(id),
  UNIQUE (booking_id, request_key)
);

CREATE INDEX ix_booking_period ON booking(status,start_at,end_at);

CREATE INDEX ix_line_asset ON booking_line(asset_id,returned_at);

CREATE INDEX ix_audit_department_time ON audit_event(department_id,created_at);

CREATE INDEX ix_customer_department ON customer(department_id);
