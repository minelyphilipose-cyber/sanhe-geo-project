package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BusinessCalendarAdminService {

    private static final int ERROR_CODE = 70042;
    private static final String MANAGE_PERMISSION = "user.manage";
    private static final String SOURCE_PATTERN = "https://timor.tech/api/holiday/year/%d/?type=Y&week=Y";
    private static final String POLICY = "普通工作日允许发布；周末、法定节假日、调休补班默认不允许自动发布";

    private final BusinessCalendarService businessCalendarService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Value("${geo.business-calendar.timezone:Asia/Shanghai}")
    private String timezone;

    public CalendarAdminStatus nextYearStatus() {
        SysUser user = requireManager();
        LocalDate today = LocalDate.now(zoneId());
        int targetYear = today.getYear() + 1;
        return buildStatus(user, today, targetYear, businessCalendarService.fileStatus(targetYear));
    }

    public CalendarAdminStatus generateNextYear(boolean force) {
        SysUser user = requireManager();
        LocalDate today = LocalDate.now(zoneId());
        int targetYear = today.getYear() + 1;
        boolean allowed = isGenerationWindow(today);
        if (!allowed && !force) {
            throw new BizException(ERROR_CODE, "下一年工作日历仅允许在每年 12 月 20 日至 12 月 31 日生成");
        }
        if (force && !isSuperAdmin(user)) {
            throw new BizException(403, "只有 super_admin 可以强制生成非窗口期工作日历");
        }
        JsonNode calendar = fetchAndBuildCalendar(targetYear);
        BusinessCalendarService.CalendarFileStatus fileStatus =
                businessCalendarService.writeRuntimeCalendar(targetYear, calendar);
        return buildStatus(user, today, targetYear, fileStatus);
    }

    private SysUser requireManager() {
        currentUserService.ensurePermission(MANAGE_PERMISSION);
        return currentUserService.requireCurrentUser();
    }

    private CalendarAdminStatus buildStatus(SysUser user,
                                            LocalDate today,
                                            int targetYear,
                                            BusinessCalendarService.CalendarFileStatus fileStatus) {
        boolean generationAllowed = isGenerationWindow(today);
        boolean superAdmin = isSuperAdmin(user);
        String message;
        if (fileStatus.exists()) {
            message = "已存在 " + targetYear + " 年工作日历，系统将自动优先使用该日历";
        } else if (generationAllowed) {
            message = "当前处于生成窗口，可生成 " + targetYear + " 年工作日历";
        } else {
            message = "未到生成窗口，普通管理员需在每年 12 月 20 日至 12 月 31 日生成下一年日历";
        }
        return new CalendarAdminStatus(
                targetYear,
                today.toString(),
                "12-20 ~ 12-31",
                generationAllowed,
                superAdmin,
                superAdmin && !generationAllowed,
                message,
                fileStatus
        );
    }

    private JsonNode fetchAndBuildCalendar(int year) {
        String sourceUrl = SOURCE_PATTERN.formatted(year);
        JsonNode apiRoot = fetchHolidayPayload(sourceUrl);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("year", year);
        root.put("source", "timor.tech/api/holiday");
        root.put("sourceUrl", sourceUrl);
        root.put("policy", POLICY);
        root.put("updatedAt", OffsetDateTime.now(zoneId()).toString());
        ArrayNode days = root.putArray("days");

        LocalDate date = LocalDate.of(year, 1, 1);
        while (date.getYear() == year) {
            days.add(buildDayNode(date, apiRoot.path("type").path(date.toString())));
            date = date.plusDays(1);
        }
        return root;
    }

    private JsonNode fetchHolidayPayload(String sourceUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ERROR_CODE, "工作日历数据源请求失败，HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException(ERROR_CODE, "工作日历数据源返回异常");
            }
            return root;
        } catch (IOException ex) {
            throw new BizException(ERROR_CODE, "工作日历数据源读取失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ERROR_CODE, "工作日历数据源请求被中断");
        }
    }

    private ObjectNode buildDayNode(LocalDate date, JsonNode sourceDay) {
        int defaultType = date.getDayOfWeek().getValue() >= 6 ? 1 : 0;
        int type = readType(sourceDay, defaultType);
        String name = readName(sourceDay, defaultName(date, type));
        int week = readWeek(sourceDay, date.getDayOfWeek().getValue());
        boolean isAdjustedWorkday = type == 3;
        boolean publishAllowed = type == 0;

        ObjectNode node = objectMapper.createObjectNode();
        node.put("date", date.toString());
        node.put("type", type);
        node.put("name", name);
        node.put("week", week);
        node.put("isWorkday", type == 0 || isAdjustedWorkday);
        node.put("isWeekend", week >= 6);
        node.put("isHoliday", type == 2);
        node.put("isAdjustedWorkday", isAdjustedWorkday);
        node.put("publishAllowed", publishAllowed);
        ArrayNode windows = node.putArray("publishWindows");
        if (publishAllowed) {
            windows.add(publishWindow("morning", "09:30", "11:30", "10:00"));
            windows.add(publishWindow("afternoon", "14:00", "17:30", "15:00"));
        }
        return node;
    }

    private ObjectNode publishWindow(String name, String start, String end, String preferredTime) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("start", start);
        node.put("end", end);
        node.put("preferredTime", preferredTime);
        return node;
    }

    private int readType(JsonNode sourceDay, int defaultType) {
        if (sourceDay == null || sourceDay.isMissingNode() || sourceDay.isNull()) {
            return defaultType;
        }
        if (sourceDay.isInt()) {
            return sourceDay.asInt(defaultType);
        }
        return sourceDay.path("type").asInt(defaultType);
    }

    private String readName(JsonNode sourceDay, String defaultName) {
        if (sourceDay == null || sourceDay.isMissingNode() || sourceDay.isNull()) {
            return defaultName;
        }
        String name = sourceDay.path("name").asText(null);
        return name == null || name.isBlank() ? defaultName : name;
    }

    private int readWeek(JsonNode sourceDay, int defaultWeek) {
        if (sourceDay == null || sourceDay.isMissingNode() || sourceDay.isNull()) {
            return defaultWeek;
        }
        return sourceDay.path("week").asInt(defaultWeek);
    }

    private String defaultName(LocalDate date, int type) {
        if (type == 3) {
            return "调休补班";
        }
        if (type == 1) {
            return date.getDayOfWeek().getValue() == 6 ? "星期六" : "星期日";
        }
        if (type == 2) {
            return "法定节假日";
        }
        return "工作日";
    }

    private boolean isGenerationWindow(LocalDate today) {
        return today.getMonth() == Month.DECEMBER && today.getDayOfMonth() >= 20;
    }

    private boolean isSuperAdmin(SysUser user) {
        return user != null
                && user.getRole() != null
                && "super_admin".equals(user.getRole().trim().toLowerCase(Locale.ROOT));
    }

    private ZoneId zoneId() {
        return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim());
    }

    public record CalendarAdminStatus(int targetYear,
                                      String today,
                                      String generationWindow,
                                      boolean generationAllowed,
                                      boolean superAdmin,
                                      boolean forceAvailable,
                                      String message,
                                      BusinessCalendarService.CalendarFileStatus calendar) {
    }
}
