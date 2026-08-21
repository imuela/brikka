-- Sprint 29 (estabilización): case_status_history.reason was varchar(50), too short for
-- CaseService.cancel()'s "REASON_CODE: <free-text comment>" concatenation — an ordinary,
-- reasonably short comment could already overflow it and crash with an unhandled 500
-- (DataIntegrityViolationException). Widened to a size that comfortably fits a real comment;
-- CaseService now also validates the comment length up front so the constraint is a backstop,
-- not the primary defense.
ALTER TABLE case_status_history ALTER COLUMN reason TYPE varchar(500);
