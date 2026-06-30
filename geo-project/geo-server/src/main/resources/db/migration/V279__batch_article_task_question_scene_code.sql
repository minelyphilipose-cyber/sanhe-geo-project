ALTER TABLE batch_article_generation_task
  ADD COLUMN question_scene_code VARCHAR(32) NULL COMMENT 'article generation question scene code' AFTER article_type_code,
  ADD KEY idx_batch_article_task_question_scene (question_scene_code);
