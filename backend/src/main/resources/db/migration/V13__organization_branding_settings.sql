-- Real persistence for the two Settings-page branding fields that weren't
-- backed by any column at all (certificate prefix, locale) -- previously
-- the whole Settings page was local component state only with a "Save"
-- button that did nothing server-side.
ALTER TABLE sf_organizations
    ADD COLUMN IF NOT EXISTS certificate_prefix TEXT NOT NULL DEFAULT 'CERT';
ALTER TABLE sf_organizations
    ADD COLUMN IF NOT EXISTS locale TEXT NOT NULL DEFAULT 'en';
