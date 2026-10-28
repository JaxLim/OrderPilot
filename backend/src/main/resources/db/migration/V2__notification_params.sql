-- Structured values behind a notification, so clients can render it in the viewer's language.
ALTER TABLE notification ADD COLUMN params JSON NULL AFTER content;
