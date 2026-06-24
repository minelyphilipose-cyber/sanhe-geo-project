ALTER TABLE project_competitor_config
  ADD COLUMN advantages VARCHAR(500) NULL AFTER aliases_json,
  ADD COLUMN disadvantages VARCHAR(500) NULL AFTER advantages;
