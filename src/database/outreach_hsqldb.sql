
ALTER TABLE ofMessageArchive ADD COLUMN deleteDate BIGINT NULL;
ALTER TABLE ofMessageArchive ADD COLUMN editDate BIGINT NULL;

INSERT INTO ofVersion (name, version) VALUES ('outreach', 1);
