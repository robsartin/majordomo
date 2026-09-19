-- Re-extraction pass for OCR (#341, ADR-0027).
--
-- V23 made EMPTY and UNSUPPORTED terminal, which was right when nothing could
-- read a scan or a photograph. OCR changes that, and those rows would
-- otherwise keep their verdict forever — the feature would apply only to
-- attachments uploaded after today.
--
-- Requeueing them here makes the migration itself the re-extraction trigger:
-- it runs once, on deploy, through the same sweep as everything else. Nothing
-- to remember to run by hand.
--
-- EXTRACTED is left alone; its text is already there. FAILED is left alone
-- too: it means the bytes could not be read at all, which OCR does not repair.

UPDATE attachments
   SET extraction_status = 'PENDING',
       text_extracted_at = NULL
 WHERE extraction_status IN ('EMPTY', 'UNSUPPORTED')
   AND archived_at IS NULL;
