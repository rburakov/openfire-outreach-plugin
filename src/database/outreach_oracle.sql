
ALTER TABLE ofMessageArchive ADD deleteDate INTEGER NULL;
ALTER TABLE ofMessageArchive ADD editDate INTEGER NULL;

INSERT INTO ofVersion (name, version) VALUES ('outreach', 1);
