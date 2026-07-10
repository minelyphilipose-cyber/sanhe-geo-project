package com.huanjing.geo.module.content.constant;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelfMediaPublishFailureCodes {
    public static final String BAIJIAHAO_PLATFORM_RATE_LIMITED = "BAIJIAHAO_PLATFORM_RATE_LIMITED";

    private static final Pattern EXPLICIT_CODE_PATTERN = Pattern.compile("^([A-Z0-9_]{3,80})[：:]");

    private static final Set<String> SCHEDULE_EXECUTION_RETRYABLE_CODES = Set.of(
            "PAGE_LOAD_TIMEOUT",
            "PLATFORM_TAB_GONE",
            "PLATFORM_TAB_REDIRECTED",
            "DOUYIN_PUBLISH_NOT_CONFIRMED",
            "TOUTIAO_PUBLISH_NOT_CONFIRMED",
            "EDITOR_NOT_READY",
            "COVER_UPLOAD_TIMEOUT",
            "SCHEDULE_DIALOG_NOT_READY",
            "PREVIEW_PAGE_NOT_READY",
            "LOCAL_HELPER_TEMPORARY_ERROR",
            "XIAOHONGSHU_FORMAT_NOT_READY",
            "XIAOHONGSHU_PUBLISH_SETTINGS_NOT_READY",
            "XIAOHONGSHU_IMAGE_GENERATION_TIMEOUT",
            "XIAOHONGSHU_PUBLISH_NOT_CONFIRMED",
            "ZHIHU_DRAFT_LOADING",
            "ZHIHU_PUBLISH_NOT_SUBMITTED",
            "ZHIHU_PUBLISH_NOT_CONFIRMED",
            "ZHIHU_COVER_UPLOAD_TIMEOUT",
            "WORKS_LIST_VERIFY_TIMEOUT",
            "BAIJIAHAO_COVER_UPLOAD_TIMEOUT",
            "BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY",
            "BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND",
            "BAIJIAHAO_PUBLISH_NOT_CONFIRMED",
            "LOCAL_AGENT_HEARTBEAT_TIMEOUT",
            BAIJIAHAO_PLATFORM_RATE_LIMITED
    );

    private static final Map<String, FailureMetadata> METADATA = Map.ofEntries(
            entry("DISTRIBUTION_QUOTA_EXHAUSTED", "分发额度已用尽", false, "补充分发额度后重新创建排期。", "OPEN_PACKAGE_QUOTA", "查看套餐额度", "navigate"),
            entry("CHANNEL_QUOTA_EXHAUSTED", "渠道额度已用完", false, "补充该平台渠道额度后重新创建排期。", "OPEN_PACKAGE_QUOTA", "查看渠道额度", "navigate"),
            entry("CHANNEL_QUOTA_UNAVAILABLE", "渠道额度配置不可用", false, "检查品牌或平台渠道额度配置。", "OPEN_PACKAGE_QUOTA", "检查额度配置", "navigate"),
            entry("ARTICLE_ALREADY_PUBLISHED", "文章已发布，不能重复分发", false, "已发布或已分发文章不能再次创建自媒体排期。", "OPEN_ARTICLE", "查看文章", "navigate"),
            entry("ARTICLE_NOT_READY", "文章状态不允许排期", false, "仅已就绪或未发布文章可创建自动排期。", "OPEN_ARTICLE", "查看文章状态", "navigate"),
            entry("NO_AVAILABLE_ACCOUNT", "没有可用自媒体账号", false, "启用账号、补充账号绑定或更换平台后重新创建排期。", "OPEN_BRAND_SELF_MEDIA_ACCOUNTS", "配置自媒体账号", "navigate"),
            entry("AUTH_EXPIRED", "平台授权已过期", false, "在品牌详情重新授权对应平台账号后重新创建排期。", "OPEN_BRAND_SELF_MEDIA_ACCOUNTS", "重新授权", "navigate"),
            entry("CLIENT_ERROR", "平台账号或内容异常", false, "检查平台账号授权、文章内容和平台返回信息后重新创建排期。", "OPEN_DIAGNOSTICS", "查看诊断", "diagnostics"),
            entry("WECHAT_API_UNAUTHORIZED", "微信公众号发布权限不足", false, "当前公众号缺少发布所需授权。请在品牌详情重新授权公众号，并确认授权时已勾选素材、草稿和发布相关权限。", "OPEN_BRAND_SELF_MEDIA_ACCOUNTS", "重新授权公众号", "navigate"),
            entry("PLATFORM_CAPABILITY_DISABLED", "平台排期能力未启用", false, "在排期能力管理中启用并验证该平台后重新创建排期。", "OPEN_SCHEDULE_CAPABILITY", "打开排期能力", "navigate"),
            entry("BACKEND_CLAIM_BLOCKED", "后台领取被阻塞", true, "检查本地助手领取状态和后台日志，等待自动重试。", "RETRY_NOW", "立即重试", "api"),
            entry("LOCAL_AGENT_OFFLINE", "本地助手离线", true, "确认本地助手和浏览器扩展在线后等待自动重试。", "OPEN_LOCAL_HELPER", "打开本地助手", "local"),
            entry("LOCAL_AGENT_HEARTBEAT_TIMEOUT", "本地助手执行心跳超时", true, "确认本地助手和 AdsPower 正常运行，系统会自动重试。", "RETRY_NOW", "立即重试", "api"),
            entry("LOCAL_HELPER_TEMPORARY_ERROR", "本地助手临时异常", true, "检查助手运行状态，等待自动重试。", "RETRY_NOW", "立即重试", "api"),
            entry("PAGE_LOAD_TIMEOUT", "页面加载或执行超时", true, "检查平台页面是否可访问，等待自动重试。", "RETRY_NOW", "立即重试", "api"),
            entry("PLATFORM_TAB_GONE", "平台页面已关闭", true, "平台标签页在填充前被关闭，系统将重新打开页面后重试。", "RETRY_NOW", "立即重试", "api"),
            entry("PLATFORM_TAB_REDIRECTED", "平台页面发生跳转", true, "平台编辑页跳转到非目标页面，系统将重新打开编辑页后重试。", "RETRY_NOW", "立即重试", "api"),
            entry("EXTENSION_HOST_PERMISSION_DENIED", "扩展无法访问平台页面", false, "确认 AdsPower 环境已加载最新生产扩展包后重新打开环境。", "OPEN_DIAGNOSTICS", "查看诊断", "diagnostics"),
            entry("DOUYIN_PUBLISH_NOT_CONFIRMED", "抖音发布结果暂未确认", true, "扩展已自动打开抖音作品管理页继续校验；若平台数据尚未出现，系统会自动重试。", "RETRY_NOW", "重新校验", "api"),
            entry("TOUTIAO_PUBLISH_NOT_CONFIRMED", "头条发布结果暂未确认", true, "扩展已自动打开头条作品管理页继续校验；若平台数据尚未出现，系统会自动重试。", "RETRY_NOW", "重新校验", "api"),
            entry("XIAOHONGSHU_PUBLISH_NOT_CONFIRMED", "小红书发布结果暂未确认", true, "扩展已自动打开笔记管理页继续校验，避免重复提交。", "RETRY_NOW", "重新校验", "api"),
            entry("ZHIHU_PUBLISH_NOT_CONFIRMED", "知乎发布结果暂未确认", true, "扩展已自动检查文章页及创作管理页；若平台数据尚未出现，系统会自动重试。", "RETRY_NOW", "重新校验", "api"),
            entry("SCHEDULE_DIALOG_NOT_READY", "定时发布弹窗未就绪", true, "等待页面稳定后自动重试。"),
            entry("SCHEDULE_TIME_OR_SELECTOR_FAILED", "定时时间或选择器失败", true, "检查平台时间控件，等待自动重试；若持续失败需更新适配器。"),
            entry("PREVIEW_PAGE_NOT_READY", "预览或回查页面未就绪", true, "等待平台页面稳定后自动重试。"),
            entry("COVER_UPLOAD_TIMEOUT", "封面上传超时", true, "检查封面素材和平台上传状态，等待自动重试。"),
            entry("EDITOR_NOT_READY", "编辑器未就绪", true, "检查平台页面加载状态，等待自动重试。"),
            entry("EDITOR_NOT_FOUND", "编辑器未找到", true, "检查页面是否进入正确编辑页，等待自动重试。"),
            entry("LOGIN_REQUIRED", "平台账号未登录", false, "打开绑定环境完成平台登录后重新创建排期。"),
            entry("FILL_FAILED", "页面填充失败", false, "查看扩展诊断信息后修复页面适配或人工处理。"),
            entry("MATERIAL_IMAGE_UNAVAILABLE", "素材图片不可用", false, "打开文章检查封面和正文图片，替换已删除或无法访问的素材后点击立即重试。", "OPEN_ARTICLE_MATERIALS", "处理素材", "navigate"),
            entry("PUBLIC_MATERIAL_NOT_FOUND", "素材公开链接失效", false, "素材已删除或公开链接已失效。打开文章替换封面/正文图片后点击立即重试。", "OPEN_ARTICLE_MATERIALS", "处理素材", "navigate"),
            entry("FILL_TOKEN_USED_OR_EXPIRED", "填充令牌已使用或过期", false, "重新领取任务或重新创建排期。"),
            entry("ACCOUNT_MISMATCH", "平台账号不一致", false, "切换到排期绑定的自媒体账号后重新创建排期。"),
            entry("IDENTITY_EXPECTATION_MISSING", "缺少账号校验信息", false, "补全自媒体账号名称；所有平台统一按账号名称校验。"),
            entry("SELF_MEDIA_ACCOUNT_NOT_FOUND", "自媒体账号不存在", false, "检查品牌自媒体账号是否已删除。"),
            entry("SELF_MEDIA_ACCOUNT_INACTIVE", "自媒体账号未启用", false, "启用账号或更换账号后重新创建排期。"),
            entry("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "未绑定浏览器环境账号", false, "在品牌详情中绑定对应平台的浏览器环境账号。"),
            entry("BROWSER_ENVIRONMENT_BINDING_INVALID", "浏览器环境绑定失效", true, "在品牌详情页重新绑定 AdsPower 环境并同步登录状态后重新校验。", "OPEN_DIAGNOSTICS", "查看诊断", "diagnostics"),
            entry("BROWSER_ENVIRONMENT_LOCKED", "浏览器环境被锁定", true, "等待环境锁释放后自动重试。"),
            entry("WORKS_LIST_VERIFY_TIMEOUT", "作品列表回查超时", true, "平台结果暂未稳定，等待自动复查或人工触发重新校验。"),
            entry("PUBLISHED_URL_PENDING", "已发布，等待发布链接", true, "平台已确认发布，系统会继续回查并补充发布链接。", "OPEN_DIAGNOSTICS", "查看回查诊断", "diagnostics"),
            entry("PUBLISH_RESULT_NOT_MATCHED", "发布结果多次未匹配", false, "打开平台作品管理页确认状态后人工确认发布或失败。"),
            entry("PUBLISH_RESULT_NOT_MATCHED_RETRYING", "发布结果暂未匹配，等待复查", true, "等待下一次自动回查。"),
            entry("PUBLISH_RESULT_RECHECK_REQUESTED", "操作员已请求重新校验发布结果", true, "已重新放回发布结果回查队列，等待本地助手领取。", "OPEN_DIAGNOSTICS", "查看诊断", "diagnostics"),
            entry("PUBLISH_RESULT_CHECK_ATTEMPT_LIMIT_EXCEEDED", "发布结果复查已达最大次数", false, "打开平台作品管理页确认状态后人工确认发布或失败。", "OPEN_DIAGNOSTICS", "查看诊断", "diagnostics"),
            entry("PUBLISH_RESULT_CHECK_FAILED", "发布结果校验失败", true, "检查本地助手和平台作品管理页后重新校验。"),
            entry("PUBLISH_CHECK_FAILED", "发布结果校验失败", true, "检查本地助手和平台作品管理页后重新校验。"),
            entry("PUBLISH_CHECK_PAGE_TIMEOUT", "发布结果页面回查超时", true, "检查本地助手和平台作品管理页后重新校验。", "RETRY_NOW", "重新校验", "api"),
            entry("LOCAL_HELPER_CLAIM_TIMEOUT", "本地助手处理超时", true, "确认本地助手运行状态，系统会自动释放锁；可重新校验。", "RETRY_NOW", "重新校验", "api"),
            entry("MANUAL_RETRY_REQUESTED", "操作员已请求立即重试", true, "已重新放回自动处理队列，等待本地助手领取。"),
            entry("MANUAL_REQUIRED_BY_OPERATOR", "操作员已转人工处理", false, "由操作员人工处理，修复后可重新触发自动处理。"),
            entry("MANUAL_CONFIRMED_FAILED", "人工确认失败", false, "已由操作员确认失败。"),
            entry("CANCELLED_BY_OPERATOR", "操作员已取消", false, "已取消，无需继续处理。"),
            entry("REPLACED_BY_OPERATOR_QUICK_SCHEDULE", "已由快速排期替换", false, "原排期已取消，系统不会继续处理。"),
            entry("REPLACED_BY_OPERATOR_QUICK_DISPATCH", "已由手动触发替换", false, "原排期已取消，系统不会继续处理。"),
            entry("BAIJIAHAO_APP_ID_REQUIRED", "百家号 ID/app_id 未填写", false, "在品牌详情的百家号账号中填写百家号 ID/app_id。"),
            entry("BAIJIAHAO_COVER_REQUIRED", "百家号缺少文章封面", false, "为文章补充封面素材后重新创建排期。"),
            entry("BAIJIAHAO_COVER_UPLOAD_ENTRY_NOT_FOUND", "百家号封面上传入口未找到", false, "页面结构可能变化，请人工核对封面区域并更新适配器。"),
            entry("BAIJIAHAO_COVER_PICKER_NOT_OPEN", "百家号封面选择弹窗未打开", true, "等待自动重试；若持续失败，检查封面区域是否被弹窗或页面状态遮挡。"),
            entry("BAIJIAHAO_COVER_UPLOAD_INPUT_NOT_FOUND", "百家号封面本地上传入口未找到", false, "页面结构可能变化，请人工核对封面弹窗并更新适配器。"),
            entry("BAIJIAHAO_COVER_UPLOAD_TIMEOUT", "百家号封面上传超时", true, "检查图片文件可用性和平台上传状态，等待自动重试。"),
            entry("BAIJIAHAO_COVER_CONFIRM_NOT_FOUND", "百家号封面确认按钮未找到", false, "页面结构可能变化，请人工核对封面确认按钮。"),
            entry("BAIJIAHAO_CONTENT_WRITTEN_TO_TITLE", "百家号正文误入标题区域", false, "正文编辑器定位异常，请人工核对页面结构并更新适配器。"),
            entry("BAIJIAHAO_UEDITOR_FILL_NOT_VISIBLE", "百家号正文编辑器未显示内容", true, "等待自动重试；若持续失败，检查 UEditor iframe 是否加载完成。"),
            entry("BAIJIAHAO_TITLE_CONTENT_COLLISION", "百家号标题和正文命中同一元素", false, "页面结构定位异常，请人工核对标题和 UEditor 区域。"),
            entry("BAIJIAHAO_SCHEDULE_TIME_TOO_SOON", "百家号定时时间过近", false, "将计划时间调整到当前时间至少 1 小时后。"),
            entry("BAIJIAHAO_SCHEDULE_TIME_TOO_LATE", "百家号定时时间超过平台范围", false, "将计划时间调整到 7 天内。"),
            entry("BAIJIAHAO_SCHEDULE_TIME_INVALID", "百家号定时时间无效", false, "检查计划发布时间。"),
            entry("BAIJIAHAO_SCHEDULE_BUTTON_NOT_FOUND", "百家号定时发布按钮未找到", false, "页面结构可能变化，请人工核对底部操作区。"),
            entry("BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY", "百家号定时发布弹窗未就绪", true, "等待自动重试；若持续失败，检查是否存在文件选择窗口或其它遮挡。"),
            entry("BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND", "百家号定时时间选项未找到", true, "等待自动重试；若持续失败，记录当前三个时间控件值用于定位。"),
            entry(BAIJIAHAO_PLATFORM_RATE_LIMITED, "百家号平台频控/点击过快", true, "平台提示点击过快，等待频控窗口后自动重试。"),
            entry("BAIJIAHAO_PUBLISH_NOT_CONFIRMED", "百家号发布成功状态未确认", true, "等待自动复查；也可打开作品管理页人工确认。"),
            entry("BAIJIAHAO_REVIEW_REJECTED", "百家号审核未通过", false, "打开百家号作品管理页查看审核原因。"),
            entry("BAIJIAHAO_WORK_WITHDRAWN", "百家号作品已撤回或删除", false, "确认作品是否被平台撤回、删除或人工撤销。"),
            entry("BAIJIAHAO_FILL_FAILED", "百家号页面填充失败", false, "查看扩展诊断信息后修复页面适配或人工处理。")
    );

    private SelfMediaPublishFailureCodes() {
    }

    public static String label(String code) {
        FailureMetadata metadata = metadata(code);
        if (metadata != null) {
            return metadata.label();
        }
        return readableFailureCode(code);
    }

    public static boolean isScheduleExecutionRetryable(String code) {
        return StringUtils.hasText(code) && SCHEDULE_EXECUTION_RETRYABLE_CODES.contains(code.trim());
    }

    public static Boolean retryable(String code) {
        FailureMetadata metadata = metadata(code);
        if (metadata != null) {
            return metadata.retryable();
        }
        if (isScheduleExecutionRetryable(code)) {
            return true;
        }
        return null;
    }

    public static String actionHint(String code) {
        FailureMetadata metadata = metadata(code);
        return metadata == null ? "" : metadata.actionHint();
    }

    public static String actionKey(String code) {
        FailureMetadata metadata = metadata(code);
        if (metadata != null && StringUtils.hasText(metadata.actionKey())) {
            return metadata.actionKey();
        }
        if (Boolean.TRUE.equals(retryable(code))) {
            return "RETRY_NOW";
        }
        return "OPEN_DIAGNOSTICS";
    }

    public static String actionLabel(String code) {
        FailureMetadata metadata = metadata(code);
        if (metadata != null && StringUtils.hasText(metadata.actionLabel())) {
            return metadata.actionLabel();
        }
        if (Boolean.TRUE.equals(retryable(code))) {
            return "立即重试";
        }
        return "查看诊断";
    }

    public static String actionKind(String code) {
        FailureMetadata metadata = metadata(code);
        if (metadata != null && StringUtils.hasText(metadata.actionKind())) {
            return metadata.actionKind();
        }
        return Boolean.TRUE.equals(retryable(code)) ? "api" : "diagnostics";
    }

    public static String classifyByMessage(String message) {
        String text = message == null ? "" : message;
        Matcher matcher = EXPLICIT_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (containsAny(text, "渠道额度", "额度用完", "额度不足")) {
            return "CHANNEL_QUOTA_EXHAUSTED";
        }
        if (containsAny(text, "没有可用账号", "暂无可用账号")) {
            return "NO_AVAILABLE_ACCOUNT";
        }
        if (containsAny(text, "api unauthorized", "48001")) {
            return "WECHAT_API_UNAUTHORIZED";
        }
        if (containsAny(text, "平台能力", "能力未启用")) {
            return "PLATFORM_CAPABILITY_DISABLED";
        }
        if (containsAny(text, "后台领取", "领取失败", "被阻塞")) {
            return "BACKEND_CLAIM_BLOCKED";
        }
        if (text.contains("百家号")) {
            if (text.contains("封面选择弹窗未打开")) return "BAIJIAHAO_COVER_PICKER_NOT_OPEN";
            if (text.contains("封面上传入口")) return "BAIJIAHAO_COVER_UPLOAD_ENTRY_NOT_FOUND";
            if (text.contains("封面本地上传")) return "BAIJIAHAO_COVER_UPLOAD_INPUT_NOT_FOUND";
            if (text.contains("封面上传完成")) return "BAIJIAHAO_COVER_UPLOAD_TIMEOUT";
            if (text.contains("正文误入标题区域")) return "BAIJIAHAO_CONTENT_WRITTEN_TO_TITLE";
            if (text.contains("标题和正文命中同一元素")) return "BAIJIAHAO_TITLE_CONTENT_COLLISION";
            if (text.contains("正文填充后页面未显示正文")) return "BAIJIAHAO_UEDITOR_FILL_NOT_VISIBLE";
            if (text.contains("定时发布下拉选项")) return "BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND";
            if (text.contains("定时发布弹窗")) return "BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY";
            if (containsAny(text, "触发过快", "点击速度太快")) return BAIJIAHAO_PLATFORM_RATE_LIMITED;
            if (text.contains("发布后未检测到成功状态")) return "BAIJIAHAO_PUBLISH_NOT_CONFIRMED";
        }
        if (containsAny(text, "Material not found", "素材不存在", "素材已删除")) {
            return "PUBLIC_MATERIAL_NOT_FOUND";
        }
        if (containsAny(text, "image content-type is not supported", "content-type is not supported", "/api/public/brand-materials/")) {
            return "MATERIAL_IMAGE_UNAVAILABLE";
        }
        if (containsAny(text, "定时发布时间", "定时发布")) {
            return "SCHEDULE_TIME_OR_SELECTOR_FAILED";
        }
        if (text.contains("账号不一致")) {
            return "ACCOUNT_MISMATCH";
        }
        if (containsAny(text, "未登录", "需登录")) {
            return "LOGIN_REQUIRED";
        }
        if (containsAny(text, "未找到", "超时")) {
            return "EDITOR_NOT_FOUND";
        }
        return "FILL_FAILED";
    }

    private static FailureMetadata metadata(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return METADATA.get(code.trim());
    }

    private static Map.Entry<String, FailureMetadata> entry(String code, String label, boolean retryable, String actionHint) {
        return entry(code, label, retryable, actionHint, null, null, null);
    }

    private static Map.Entry<String, FailureMetadata> entry(String code,
                                                            String label,
                                                            boolean retryable,
                                                            String actionHint,
                                                            String actionKey,
                                                            String actionLabel,
                                                            String actionKind) {
        return Map.entry(code, new FailureMetadata(label, retryable, actionHint, actionKey, actionLabel, actionKind));
    }

    private static String readableFailureCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return "未识别异常（" + code.trim() + "）";
    }

    private static boolean containsAny(String text, String... needles) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String needle : needles) {
            if (StringUtils.hasText(needle) && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record FailureMetadata(String label,
                                   boolean retryable,
                                   String actionHint,
                                   String actionKey,
                                   String actionLabel,
                                   String actionKind) {
    }
}
