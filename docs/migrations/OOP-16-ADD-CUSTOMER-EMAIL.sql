-- REVIEW before running against MySQL. Do not run if the email column/index already exists.
-- Ensure backups and confirm there are no existing duplicate email values.
-- Existing staff accounts may have NULL email. New CUSTOMER sign-ups require an email.
ALTER TABLE staff_users
  ADD COLUMN email VARCHAR(254) NULL,
  ADD UNIQUE INDEX ux_staff_users_email (email);
