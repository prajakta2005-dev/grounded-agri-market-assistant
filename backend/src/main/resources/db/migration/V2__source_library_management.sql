ALTER TABLE source_document
    ADD COLUMN managed_by_user boolean NOT NULL DEFAULT false,
    ADD COLUMN source_type varchar(16) NOT NULL DEFAULT 'LINK',
    ADD COLUMN file_name text NOT NULL DEFAULT '';

ALTER TABLE source_document
    ADD CONSTRAINT source_document_source_type_check CHECK (source_type IN ('LINK', 'UPLOAD'));

CREATE INDEX source_document_managed_idx ON source_document (managed_by_user, id);
