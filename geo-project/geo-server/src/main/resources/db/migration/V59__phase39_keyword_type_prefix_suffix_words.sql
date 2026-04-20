-- ============================================================
-- V59: import type-specific prefix and suffix words for keyword groups
-- ============================================================

-- brand prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58FA3E7A291E5A5BDE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE79FA5E5908DE79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE68E92E5908DE5898DE58D81E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58D81E5A4A7 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69C80E5A5BDE79A84 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5A4B4E983A8 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9A286E58588E79A84 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5A4A7E59381E7898C USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE88081E7898C USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE696B0E99490 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9AB98E7ABAF USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE680A7E4BBB7E6AF94E9AB98E79A84 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59BBDE4BAA7 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8BF9BE58FA3 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69CACE59C9F USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE585A8E59BBDE8BF9EE99481 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B88AE5B882 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0x353030E5BCBA USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B880E7BABF USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BA8CE7BABF USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5B08FE4BC97E4BD86E5A5BDE794A8E79A84 USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5A4AEE4BC81 USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59BBDE4BC81 USING utf8mb4) AS word_text, 230 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6B091E890A5 USING utf8mb4) AS word_text, 240 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9BE99E5A4B4 USING utf8mb4) AS word_text, 250 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- brand suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE68E92E8A18CE6A69C USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE68EA8E88D90 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58D81E5A4A7E59381E7898C USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE68E92E5908D USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE4B8AAE59381E7898CE5A5BD USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE69C89E593AAE4BA9B USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE5AFB9E6AF94 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE6A69CE58D95 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BB80E4B988E59381E7898CE5A5BD USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898C746F703130 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE5A4A7E585A8 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE79B98E782B9 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE6B58BE8AF84 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE79FA5E5908DE59381E7898C USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE58FA3E7A291E68E92E8A18C USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5A4B4E983A8E59381E7898CE69C89E593AAE4BA9B USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE6808EE4B988E98089 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE981BFE59D91E68C87E58D97 USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59BBDE4BAA7E59381E7898CE68EA8E88D90 USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE9AB98E7ABAFE59381E7898CE69C89E593AAE4BA9B USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE680A7E4BBB7E6AF94E59381E7898CE68EA8E88D90 USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE696B0E585B4E59381E7898CE69C89E593AAE4BA9B USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE88081E7898CE5AD90E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 230 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59381E7898CE4BC98E58AA3E58ABFE58886E69E90 USING utf8mb4) AS word_text, 240 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- location prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69CACE59CB0 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE99984E8BF91E79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE591A8E8BEB9E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5908CE59F8E USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5BD93E59CB0 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5B882E58CBA USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9838AE58CBA USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7BABFE4B88B USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5B0B1E8BF91 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AEB6E997A8E58FA3E79A84 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7A6BBE68891E69C80E8BF91E79A84 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7A4BEE58CBA USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69CACE5B882 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69CACE58CBA USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8A197E98193 USING utf8mb4) AS word_text, 150 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- location suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE9878CE69C89 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59CB0E59D80E59CA8E593AA USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E58EBB USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59CA8E593AAE9878C USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE99984E8BF91E68EA8E88D90 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69CACE59CB0E68EA8E88D90 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE997A8E5BA97E59CB0E59D80 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7BABFE4B88BE5BA97 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58886E5B883E59CA8E593AAE4BA9BE58CBA USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A686E79B96E88C83E59BB4 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C8DE58AA1E58CBAE59F9F USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5908CE59F8EE68EA8E88D90 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59084E58CBAE58886E5B883 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B0B1E8BF91E98089E68BA9 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE591A8E8BEB9E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69CACE59CB0E68E92E8A18C USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4B88AE997A8E69C8DE58AA1E88C83E59BB4 USING utf8mb4) AS word_text, 170 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- industry prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B893E4B89AE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6ADA3E8A784E79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69D83E5A881E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8B584E6B7B1E79A84 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9A286E58588E79A84 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B880E7AB99E5BC8F USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7BBBCE59088E680A7 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE585A8E696B9E4BD8D USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AE9AE588B6E58C96 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE699BAE883BDE58C96 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE695B0E5AD97E58C96 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE78EB0E4BBA3E58C96 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6A087E58786E58C96 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8A784E6A8A1E58C96 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5889BE696B0E59E8B USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE696B0E585B4E79A84 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BCA0E7BB9F USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59E82E79BB4E9A286E59F9F USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7BB86E58886E8A18CE4B89A USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5A4B4E983A8 USING utf8mb4) AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- industry suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE78EB0E78AB6 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE8B68BE58ABF USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE58886E69E90 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE5898DE699AF USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE68AA5E5918A USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE68E92E5908D USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE6A087E58786 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE8A784E88C83 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58F91E5B195E5898DE699AFE6808EE4B988E6A0B7 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B882E59CBAE8A784E6A8A1E5A49AE5A4A7 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69CAAE69DA5E8B68BE58ABF USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE7979BE782B9 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE8A7A3E586B3E696B9E6A188 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE799BDE79AAEE4B9A6 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE9BE99E5A4B4E4BC81E4B89A USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE58F91E5B195E58E86E7A88B USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE694BFE7AD96 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BB8EE4B89AE997A8E6A79B USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE588A9E6B6A6E78E87 USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE7AB9EE4BA89E6A0BCE5B180 USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7BB86E58886E8B59BE98193E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BAA7E4B89AE993BEE58886E69E90 USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A18CE4B89AE69CBAE4BC9A USING utf8mb4) AS word_text, 230 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- decision prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE580BCE5BE97E98089E79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE68EA8E88D90E79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE98082E59088E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59088E98082E79A84 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE79086E683B3E79A84 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9A696E98089E79A84 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BC98E8B4A8E79A84 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58892E7AE97E79A84 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE9AB98E680A7E4BBB7E6AF94E79A84 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5858DE8B4B9E79A84 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE585A5E997A8E7BAA7 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8BF9BE998B6E78988 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69797E888B0 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59FBAE7A180E78988 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BC81E4B89AE7BAA7 USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5B08FE59E8B USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B8ADE59E8B USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5A4A7E59E8B USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8BDBBE9878FE7BAA7 USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE585A8E58A9FE883BD USING utf8mb4) AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- decision suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E98089 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5A682E4BD95E98089E68BA9 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E68BA9E68C87E58D97 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E8B4ADE694BBE795A5 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE981BFE59D91E68C87E58D97 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6B3A8E6848FE4BA8BE9A1B9 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E68BA9E6A087E58786 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE588A4E696ADE4BE9DE68DAE USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98082E59088E4BB80E4B988E4BABA USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BB80E4B988E68385E586B5E4B88BE98089 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E68BA9E5BBBAE8AEAE USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8A681E6B3A8E6848FE4BB80E4B988 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C89E4BB80E4B988E59D91 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B8B8E8A781E8AFAFE58CBA USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE696B0E6898BE6808EE4B988E98089 USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE9A284E7AE97E69C89E99990E6808EE4B988E98089 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E99499E4BA86E6808EE4B988E58A9E USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6A0B8E5BF83E88083E89991E59BA0E7B4A0 USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE586B3E7AD96E6B885E58D95 USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE98089E59E8BE5AFB9E6AF94 USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE585A5E997A8E68C87E58D97 USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8B4ADE4B9B0E5BBBAE8AEAE USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8AF84E4BCB0E7BBB4E5BAA6E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 230 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- transaction prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BEBFE5AE9CE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AE9EE683A0E79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE68993E68A98E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BC98E683A0E79A84 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE789B9E4BBB7 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE99990E697B6 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59BA2E8B4AD USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE689B9E58F91 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5858DE8B4B9E8AF95E794A8 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BD8EE4BBB7 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58C85E982AE USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6ADA3E59381 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AE98E696B9 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE79BB4E890A5 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58E82E5AEB6E79BB4E99480 USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BF83E99480 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6B885E4BB93 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7A792E69D80 USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BC9AE59198E4BBB7 USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE696B0E4BABAE4B893E4BAAB USING utf8mb4) AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- transaction suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5A49AE5B091E992B1 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BBB7E6A0BCE8A1A8 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE694B6E8B4B9E6A087E58786 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E694B6E8B4B9 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE68AA5E4BBB7E58D95 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8B4B9E794A8E6988EE7BB86 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BBB7E6A0BCE5AFB9E6AF94 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C80E696B0E4BBB7E6A0BC USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BC98E683A0E6B4BBE58AA8 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE68A98E689A3E4BFA1E681AF USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BF83E99480E4BBB7 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59BA2E8B4ADE4BBB7 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5858DE8B4B9E8AF95E794A8E585A5E58FA3 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59CA8E593AAE4B9B0 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E4B9B0 USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8B4ADE4B9B0E6B8A0E98193 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5AE98E696B9E8B4ADE4B9B0E993BEE68EA5 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8AEA2E8B4ADE696B9E5BC8F USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5A597E9A490E4BBB7E6A0BC USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B9B4E8B4B9E5A49AE5B091 USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C88E8B4B9E5A49AE5B091 USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE680A7E4BBB7E6AF94E58886E69E90 USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7A08DE4BBB7E68A80E5B7A7 USING utf8mb4) AS word_text, 230 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE79C81E992B1E694BBE795A5 USING utf8mb4) AS word_text, 240 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE99A90E8978FE694B6E8B4B9E9A1B9 USING utf8mb4) AS word_text, 250 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- comparison prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69BB4E5A5BDE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69BBFE4BBA3 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7B1BBE4BCBC USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5908CE7B1BB USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58D87E7BAA7E78988 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5B9B3E69BBF USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AFB9E6A087 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE7AB9EE4BA89 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5908CE4BBB7E4BD8D USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5908CE7BAA7E588AB USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE59BBDE4BAA7E69BBFE4BBA3 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5BC80E6BA90E69BBFE4BBA3 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5858DE8B4B9E69BBFE4BBA3 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BD8EE68890E69CACE69BBFE4BBA3 USING utf8mb4) AS word_text, 140 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- comparison suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5928C5858E593AAE4B8AAE5A5BD USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5AFB9E6AF94E58886E69E90 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58CBAE588ABE698AFE4BB80E4B988 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C89E4BB80E4B988E4B88DE5908C USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BC98E7BCBAE782B9E5AFB9E6AF94 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6A8AAE59091E5AFB9E6AF94 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE585A8E696B9E4BD8DE5AFB9E6AF94 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6B7B1E5BAA6E5AFB9E6AF94 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE680A7E883BDE5AFB9E6AF94 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BBB7E6A0BCE5AFB9E6AF94 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58A9FE883BDE5AFB9E6AF94 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BD93E9AA8CE5AFB9E6AF94 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE4B8AAE69BB4E580BCE5BE97 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E98089 USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE59084E69C89E4BB80E4B988E4BC98E58ABF USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B7AEE5BC82E59CA8E593AAE9878C USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8B081E69BB4E8839CE4B880E7ADB9 USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5AE9EE6B58BE5AFB9E6AF94 USING utf8mb4) AS word_text, 180 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69BBFE4BBA3E696B9E6A188E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 190 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B9B3E69BBFE68EA8E88D90 USING utf8mb4) AS word_text, 200 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5908CE7B1BBE4BAA7E59381E5AFB9E6AF94 USING utf8mb4) AS word_text, 210 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE8BF81E7A7BBE68890E69CACE9AB98E59097 USING utf8mb4) AS word_text, 220 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE58887E68DA2E99ABEE5BAA6E5A4A7E59097 USING utf8mb4) AS word_text, 230 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE585BCE5AEB9E680A7E5AFB9E6AF94 USING utf8mb4) AS word_text, 240 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- competitor prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6AF945858E69BB4E5A5BDE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0x5858E79A84E69BBFE4BBA3E59381 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B88DE8BE935858E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8B685E8B68A5858E79A84 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8B79F5858E7B1BBE4BCBCE79A84 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5AFB9E6A0875858E79A84 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0x504BE68E895858E79A84 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58FAFE4BBA5E69BBFE4BBA35858E79A84 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0x5858E79A84E5B9B3E69BBF USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE5928C5858E7AB9EE4BA89E79A84 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0x5858E79A84E7AB9EE4BA89E5AFB9E6898B USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6AF945858E4BEBFE5AE9CE79A84 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6AF945858E5A5BDE794A8E79A84 USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6AF945858E4B893E4B89AE79A84 USING utf8mb4) AS word_text, 140 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- competitor suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E69C89E593AAE4BA9B USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE4BA89E5AFB9E6898BE58886E69E90 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5B882E59CBAE7AB9EE59381 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69BBFE4BBA3E4BAA7E59381 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E5AFB9E6AF94E68AA5E5918A USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5928CE7AB9EE59381E79A84E58CBAE588AB USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E4BC98E58AA3E58ABF USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4B8BAE4BB80E4B988E98089E5AE83E4B88DE980895858 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE4BB8E5858E8BF81E7A7BBE8BF87E69DA5 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6AF945858E5A5BDE59CA8E593AA USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5928C5858E79A84E6A0B8E5BF83E5B7AEE5BC82 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE883BDE590A6E69BBFE4BBA35858 USING utf8mb4) AS word_text, 120 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE79BB8E6AF945858E79A84E4BC98E58ABF USING utf8mb4) AS word_text, 130 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E58886E69E90E68AA5E5918A USING utf8mb4) AS word_text, 140 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE5908CE7B1BBE7AB9EE59381E79B98E782B9 USING utf8mb4) AS word_text, 150 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E58A9FE883BDE5AFB9E785A7E8A1A8 USING utf8mb4) AS word_text, 160 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE794A8E688B7E4BB8E5858E8BDACE8BF87E69DA5E79A84E4BD93E9AA8C USING utf8mb4) AS word_text, 170 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE7AB9EE59381E5B882E59CBAE4BBBDE9A29DE5AFB9E6AF94 USING utf8mb4) AS word_text, 180 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- qa prefix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE58FA3E7A291E5A5BDE79A84 USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6AF94E8BE83E5A5BDE79A84 USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE99DA0E8B0B1E79A84 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69C89E5AE9EE58A9BE79A84 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4B893E4B89AE79A84 USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE79FA5E5908DE79A84 USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE8AF84E4BBB7E9AB98E79A84 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE4BC98E7A780E79A84 USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69C89E5908DE79A84 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE580BCE5BE97E4BFA1E8B596E79A84 USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE6ADA3E8A784E79A84 USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'prefix' AS affix_kind, CONVERT(0xE69C89E4BF9DE99A9CE79A84 USING utf8mb4) AS word_text, 120 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

-- qa suffix
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E5A5BD USING utf8mb4) AS word_text, 10 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E5BCBA USING utf8mb4) AS word_text, 20 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E99DA0E8B0B1 USING utf8mb4) AS word_text, 30 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E69D83E5A881 USING utf8mb4) AS word_text, 40 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE4B8AAE5A5BD USING utf8mb4) AS word_text, 50 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E4B893E4B89A USING utf8mb4) AS word_text, 60 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E58FAFE99DA0 USING utf8mb4) AS word_text, 70 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE69C89E593AAE4BA9B USING utf8mb4) AS word_text, 80 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE6808EE4B988E98089 USING utf8mb4) AS word_text, 90 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E6AF94E8BE83E5A5BD USING utf8mb4) AS word_text, 100 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E58FA3E7A291E5A5BD USING utf8mb4) AS word_text, 110 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'suffix' AS affix_kind, CONVERT(0xE593AAE5AEB6E8AF84E4BBB7E9AB98 USING utf8mb4) AS word_text, 120 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);
