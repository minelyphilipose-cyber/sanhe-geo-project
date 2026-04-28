#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
拓词管理 V1.5 阶段一关键词自然度评估 - 抽样脚本
调用 preview 接口 5 个用例 × 6 个 type,从结果中等概率随机抽 17 条/type,共 102 条。
输出:naturalness-sample-batch10.csv
"""
import csv
import random

import requests

# 配置
BASE_URL = 'http://localhost:8080/api'
USERNAME = 'admin'
PASSWORD = 'admin123'
COMPANY_ID = 4

# 设置随机种子保证可复现(评估完毕后可改种子重抽)
random.seed(44)

# 测试核心词
CORE_WORDS = {
    'toC_brand': ['小米', '华为', '苹果', '海底捞', '星巴克'],
    'toB_brand': ['钉钉', '飞书', '用友', '金蝶', '蓝凌'],
    'toC_category': ['手机', '笔记本', '冰箱', '西装', '化妆品'],
    'toB_category': ['OA软件', 'CRM', 'ERP', '协作工具', 'BI系统'],
    'industry': ['双开门冰箱', '防盗门', '全屋净水', '西装', '数控机床'],
}

FUNCTION_CORE_BY_INDUSTRY = {
    'door_window': ['防盗门', '推拉门', '断桥铝窗', '木门', '隔音窗'],
    'appliance': ['双开门冰箱', '滚筒洗衣机', '空气炸锅', '吸油烟机', '空调'],
    'building_material': ['乳胶漆', '瓷砖', '地板', '集成吊顶', '防水涂料'],
    'fmcg': ['儿童奶粉', '酸奶', '洗发水', '牙膏', '即食麦片'],
    'industrial': ['数控机床', '工业机器人', '注塑机', '激光切割机', 'CNC'],
    'clothing': ['西装', '运动鞋', '冲锋衣', '羽绒服', '童装'],
}

QA_CORE_WORDS = ['冰箱', '洗衣机', '空调', '汽车', '手机', '电脑', '笔记本', '相机', '吸尘器', '热水器']

# 6 个新类型
TYPES = ['brand', 'decision', 'transaction', 'comparison', 'qa', 'function']

# function 行业(按 PRD 3.6.4)
FUNCTION_INDUSTRIES = ['appliance', 'door_window', 'building_material', 'fmcg', 'industrial', 'clothing']


def login_headers():
    """登录并返回 Bearer Token 请求头。"""
    r = requests.post(
        f'{BASE_URL}/auth/login',
        json={'username': USERNAME, 'password': PASSWORD},
        timeout=30,
    )
    r.encoding = 'utf-8'
    data = r.json()
    token = data.get('data', {}).get('accessToken')
    if not token:
        raise RuntimeError(f'login failed: {data}')
    return {'Authorization': f'Bearer {token}'}


HEADERS = login_headers()


def call_preview(payload):
    """调用 preview 接口"""
    headers = {**HEADERS, 'Content-Type': 'application/json'}
    r = requests.post(f'{BASE_URL}/keyword-groups/preview', json=payload, headers=headers, timeout=30)
    r.encoding = 'utf-8'
    data = r.json()
    if data.get('code') != 200 and data.get('code') != 0:
        print(f"  ERROR: {data}")
        return []
    return data.get('data', {}).get('keywords', [])


def call_options(type_, industry_tag=None):
    """调用 options 接口拿到候选词"""
    params = {'type': type_}
    if industry_tag:
        params['industryTag'] = industry_tag
    r = requests.get(f'{BASE_URL}/keyword-affix-words/options', params=params, headers=HEADERS, timeout=30)
    r.encoding = 'utf-8'
    return r.json().get('data', {})


def to_word_items(words):
    return [{'wordText': w, 'source': 'system', 'sortOrder': i * 10} for i, w in enumerate(words)]


def core_kind_for_case(case_id):
    return 'brand' if case_id in (0, 1) else 'category'


def core_pool_for_case(case_id):
    if core_kind_for_case(case_id) == 'brand':
        return CORE_WORDS['toC_brand'] + CORE_WORDS['toB_brand']
    return CORE_WORDS['toC_category'] + CORE_WORDS['toB_category']


def build_payload_for_case(t, case_id, options, industry_tag=None):
    """为指定 type 和 case 构造 preview payload"""
    base = {
        'companyId': COMPANY_ID,
        'type': t,
        'count': 100,
    }
    prefix_words = [w['wordText'] for w in options.get('prefixWords', [])][:5]
    suffix_words = [w['wordText'] for w in options.get('suffixWords', [])][:5]
    industry_words = [w['wordText'] for w in options.get('industryWords', [])][:3]
    compare_words = [w['wordText'] for w in options.get('compareWords', [])][:6]

    if t == 'comparison':
        # case 1-3: 不同 coreA/coreB 组合
        # case 4: 边界(1+1+1+1)
        # case 5: 多 compareWord
        if case_id == 0:
            core_a, core_b = ['小米', '华为'], ['苹果', '三星']
        elif case_id == 1:
            core_a, core_b = ['钉钉'], ['飞书', '企业微信']
        elif case_id == 2:
            core_a, core_b = ['手机', '笔记本'], ['冰箱']
        elif case_id == 3:
            core_a, core_b = ['OA软件'], ['CRM']  # 边界
            compare_words = compare_words[:1]
            suffix_words = suffix_words[:1]
        else:
            core_a, core_b = ['ERP'], ['协作工具']  # 多 compareWord
            compare_words = compare_words[:6]  # 全 6 个
        base['areaEnabled'] = False
        base['columns'] = {
            'coreWordsA': to_word_items(core_a),
            'compareWords': to_word_items(compare_words),
            'coreWordsB': to_word_items(core_b),
            'suffixWords': to_word_items(suffix_words),
            'areaWords': [], 'prefixWords': [],
            'coreWords': [], 'industryWords': [],
        }
    else:
        # 普通类型 5 个 case
        if t == 'function':
            core_pool = FUNCTION_CORE_BY_INDUSTRY[industry_tag]
        elif t == 'qa':
            core_pool = QA_CORE_WORDS
        else:
            core_pool = core_pool_for_case(case_id)
        core_word = random.choice(core_pool)
        if case_id == 0:
            cores = [core_word]
        elif case_id == 1:
            cores = random.sample(core_pool, 2)  # 多核心
        elif case_id == 2:
            cores = [core_word]  # 地区开启(decision/transaction/function)
        elif case_id == 3:
            cores = [core_word]  # 边界:只填核心词
            prefix_words = []
            industry_words = []
            suffix_words = suffix_words[:3]
        else:
            cores = [core_word]  # 后缀大量
            suffix_words = [w['wordText'] for w in options.get('suffixWords', [])][:8]

        base['areaEnabled'] = (case_id == 2 and t in ('decision', 'transaction', 'function'))
        base['columns'] = {
            'areaWords': to_word_items(['北京', '上海', '深圳']) if base['areaEnabled'] else [],
            'prefixWords': to_word_items(prefix_words),
            'coreWords': to_word_items(cores),
            'industryWords': to_word_items(industry_words),
            'suffixWords': to_word_items(suffix_words),
            'coreWordsA': [], 'compareWords': [], 'coreWordsB': [],
        }

    # function 类型必填 industryTag
    if t == 'function':
        base['functionIndustryTag'] = industry_tag

    return base


def sample_one_type(t):
    """为单个 type 抽样"""
    print(f"\n=== 抽样 {t} ===")
    all_keywords = []
    for case_id in range(5):
        # function 类型每 case 用不同 industry
        industry_tag = None
        if t == 'function':
            industry_tag = FUNCTION_INDUSTRIES[case_id]
        options = call_options(t, industry_tag)
        payload = build_payload_for_case(t, case_id, options, industry_tag)
        keywords = call_preview(payload)
        print(f"  case {case_id}: 生成 {len(keywords)} 条")
        # 等概率随机抽 4 条
        chosen = random.sample(keywords, min(4, len(keywords))) if keywords else []
        for kw in chosen:
            all_keywords.append({
                'type': t,
                'case_id': case_id,
                'industry_tag': industry_tag or '',
                'core_kind': core_kind_for_case(case_id),
                'keyword': kw,
            })
    # 从 20 条候选(5 case × 4 条)分层抽 17 条,保证品牌核心词和品类核心词都覆盖。
    brand_items = [item for item in all_keywords if item['core_kind'] == 'brand']
    category_items = [item for item in all_keywords if item['core_kind'] == 'category']
    selected = []
    selected.extend(random.sample(brand_items, min(8, len(brand_items))))
    selected.extend(random.sample(category_items, min(8, len(category_items))))
    selected_ids = {id(item) for item in selected}
    remaining = [item for item in all_keywords if id(item) not in selected_ids]
    if len(selected) < min(17, len(all_keywords)):
        selected.extend(random.sample(remaining, min(min(17, len(all_keywords)) - len(selected), len(remaining))))
    return random.sample(selected, len(selected))


def main():
    all_samples = []
    for t in TYPES:
        all_samples.extend(sample_one_type(t))

    # 打乱顺序后写 CSV(评估时盲评)
    random.shuffle(all_samples)

    out_path = 'geo-project/docs/naturalness-sample-batch10.csv'
    with open(out_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(
            f,
            fieldnames=['sample_id', 'type', 'case_id', 'industry_tag', 'keyword', 'score', 'mismatch_tag', 'note'],
        )
        writer.writeheader()
        for i, s in enumerate(all_samples):
            s['sample_id'] = i + 1
            s['score'] = ''  # AI 训练师填
            s['mismatch_tag'] = ''
            s['note'] = ''
            s.pop('core_kind', None)
            writer.writerow(s)

    print(f"\n抽样完成 {len(all_samples)} 条 -> {out_path}")


if __name__ == '__main__':
    main()
