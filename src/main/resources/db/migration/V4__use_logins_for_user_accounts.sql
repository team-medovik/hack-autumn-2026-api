ALTER TABLE user_accounts RENAME COLUMN email TO login;

ALTER TABLE user_accounts
    ADD CONSTRAINT user_accounts_login_length CHECK (char_length(login) > 3);

ALTER INDEX idx_user_accounts_email RENAME TO idx_user_accounts_login;
