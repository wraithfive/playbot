-- OAuth2 Authorized Client table
-- This table stores OAuth2 access tokens and refresh tokens for logged-in users
-- Schema from: https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html

CREATE TABLE IF NOT EXISTS oauth2_authorized_client (
  client_registration_id VARCHAR(100) NOT NULL,
  principal_name VARCHAR(200) NOT NULL,
  access_token_type VARCHAR(100) NOT NULL,
  access_token_value BLOB NOT NULL,
  access_token_issued_at TIMESTAMP NOT NULL,
  access_token_expires_at TIMESTAMP NOT NULL,
  access_token_scopes VARCHAR(1000) DEFAULT NULL,
  refresh_token_value BLOB DEFAULT NULL,
  refresh_token_issued_at TIMESTAMP DEFAULT NULL,
  PRIMARY KEY (client_registration_id, principal_name)
);
