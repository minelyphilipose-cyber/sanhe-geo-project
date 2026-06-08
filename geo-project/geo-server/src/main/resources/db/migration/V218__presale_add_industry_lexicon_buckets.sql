INSERT INTO presale_lexicon_bucket
    (bucket_code, bucket_name, customer_term, conversion_term, default_industry_short,
     enabled, source, config_version, remark)
VALUES
    ('CATERING', '餐饮美食', '顾客', '到店', '餐饮',
     1, 'SEED', 'v1', '餐饮、美食、饮品、烘焙、火锅、烧烤、小吃'),
    ('LOCAL_STORE', '到店生活服务', '顾客', '到店', '门店',
     1, 'SEED', 'v1', '美容、美发、美甲、SPA、养生、按摩、足疗'),
    ('FITNESS', '健身运动', '会员', '到店体验', '健身',
     1, 'SEED', 'v1', '健身、瑜伽、游泳、舞蹈、拳馆、运动馆'),
    ('AUTO', '汽车服务', '车主', '到店', '汽车',
     1, 'SEED', 'v1', '4S、汽修、保养、洗车、美容改装、二手车'),
    ('REAL_ESTATE', '房产置业', '客户', '看房', '房产',
     1, 'SEED', 'v1', '新房、二手房、租房、中介、楼盘'),
    ('HOSPITALITY', '住宿旅游', '客人', '预订', '酒店',
     1, 'SEED', 'v1', '酒店、民宿、旅游、景区、农家乐'),
    ('PET', '宠物服务', '宠主', '到店', '宠物',
     1, 'SEED', 'v1', '宠物店、宠物医院、宠物美容、寄养'),
    ('AESTHETIC', '医美/美容医疗', '求美者', '面诊', '医美',
     1, 'SEED', 'v1', '医美、整形、皮肤管理、轻医美'),
    ('WEDDING', '婚庆摄影', '准新人', '预约', '婚庆',
     1, 'SEED', 'v1', '婚庆、婚纱摄影、写真、宴会策划'),
    ('B2B', '企业服务/批发', '客户', '询价', '企业服务',
     1, 'SEED', 'v1', '制造、批发、设备、原材料、工业品')
ON DUPLICATE KEY UPDATE
    bucket_name = VALUES(bucket_name),
    customer_term = VALUES(customer_term),
    conversion_term = VALUES(conversion_term),
    default_industry_short = VALUES(default_industry_short),
    enabled = VALUES(enabled),
    source = VALUES(source),
    remark = VALUES(remark);
