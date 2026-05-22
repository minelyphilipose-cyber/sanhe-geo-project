UPDATE batch_article_generation_task
SET suggested_platform_codes = CAST(REPLACE(CAST(suggested_platform_codes AS CHAR), 'douyin_image_text', 'douyin') AS JSON)
WHERE suggested_platform_codes IS NOT NULL
  AND CAST(suggested_platform_codes AS CHAR) LIKE '%douyin_image_text%'
  AND JSON_VALID(REPLACE(CAST(suggested_platform_codes AS CHAR), 'douyin_image_text', 'douyin'));

UPDATE batch_article_generation_task
SET selected_platform_codes = CAST(REPLACE(CAST(selected_platform_codes AS CHAR), 'douyin_image_text', 'douyin') AS JSON)
WHERE selected_platform_codes IS NOT NULL
  AND CAST(selected_platform_codes AS CHAR) LIKE '%douyin_image_text%'
  AND JSON_VALID(REPLACE(CAST(selected_platform_codes AS CHAR), 'douyin_image_text', 'douyin'));
