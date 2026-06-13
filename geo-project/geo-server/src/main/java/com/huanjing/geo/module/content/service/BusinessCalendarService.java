package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessCalendarService {
    private static final int ERROR_CODE = 70041;
    private static final String RESOURCE_PATTERN = "calendar/business-calendar-%d.json";
    private static final List<PublishWindow> DEFAULT_WINDOWS = List.of(
            new PublishWindow("morning", LocalTime.of(9, 15), LocalTime.of(11, 30), LocalTime.of(9, 15)),
            new PublishWindow("afternoon", LocalTime.of(14, 30), LocalTime.of(17, 30), LocalTime.of(14, 30))
    );

    private final ObjectMapper objectMapper;

    public List<BusinessDay> publishDays(YearMonth month, boolean includeAdjustedWorkdays) {
        if (month == null) {
            throw new BizException(ERROR_CODE, "targetMonth is required");
        }
        JsonNode root = loadCalendar(month.getYear());
        List<BusinessDay> result = new ArrayList<>();
        for (JsonNode dayNode : root.path("days")) {
            LocalDate date = parseDate(dayNode.path("date").asText(null));
            if (date == null || !YearMonth.from(date).equals(month)) {
                continue;
            }
            boolean adjustedWorkday = dayNode.path("isAdjustedWorkday").asBoolean(false);
            boolean publishAllowed = dayNode.path("publishAllowed").asBoolean(false)
                    || (includeAdjustedWorkdays && adjustedWorkday);
            if (!publishAllowed) {
                continue;
            }
            List<PublishWindow> windows = readWindows(dayNode.path("publishWindows"));
            if (windows.isEmpty() && includeAdjustedWorkdays && adjustedWorkday) {
                windows = DEFAULT_WINDOWS;
            }
            if (windows.isEmpty()) {
                continue;
            }
            result.add(new BusinessDay(
                    date,
                    dayNode.path("type").asInt(0),
                    dayNode.path("name").asText("工作日"),
                    dayNode.path("week").asInt(0),
                    adjustedWorkday,
                    windows
            ));
        }
        result.sort(Comparator.comparing(BusinessDay::date));
        return result;
    }

    public List<PublishSlot> selectEvenly(YearMonth month, int count, boolean includeAdjustedWorkdays) {
        if (count <= 0) {
            return List.of();
        }
        List<BusinessDay> days = publishDays(month, includeAdjustedWorkdays);
        if (days.isEmpty()) {
            throw new BizException(ERROR_CODE, "目标月份没有可用于自动排期的工作日");
        }
        if (count <= days.size()) {
            return selectOneSlotPerDay(days, count);
        }
        List<PublishSlot> allSlots = days.stream()
                .flatMap(day -> day.windows().stream().map(window -> toSlot(day, window)))
                .sorted(Comparator.comparing(PublishSlot::plannedAt))
                .toList();
        if (count > allSlots.size()) {
            throw new BizException(ERROR_CODE, "目标月份工作日上午/下午排期容量不足，请扩大允许日期或减少任务数量");
        }
        return selectEvenlyFromSlots(allSlots, count);
    }

    private List<PublishSlot> selectOneSlotPerDay(List<BusinessDay> days, int count) {
        List<PublishSlot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int dayIndex = Math.min(days.size() - 1, (int) Math.floor((double) i * days.size() / count));
            BusinessDay day = days.get(dayIndex);
            PublishWindow window = day.windows().get(i % day.windows().size());
            result.add(toSlot(day, window));
        }
        return result;
    }

    private List<PublishSlot> selectEvenlyFromSlots(List<PublishSlot> slots, int count) {
        List<PublishSlot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int slotIndex = Math.min(slots.size() - 1, (int) Math.floor((double) i * slots.size() / count));
            result.add(slots.get(slotIndex));
        }
        return result;
    }

    private PublishSlot toSlot(BusinessDay day, PublishWindow window) {
        return new PublishSlot(
                day.date(),
                window.name(),
                window.start(),
                window.end(),
                day.date().atTime(window.preferredTime()),
                day.type(),
                day.dayName(),
                day.week(),
                day.adjustedWorkday()
        );
    }

    private JsonNode loadCalendar(int year) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATTERN.formatted(year));
        if (!resource.exists()) {
            throw new BizException(ERROR_CODE, "缺少 " + year + " 年工作日历文件");
        }
        try {
            return objectMapper.readTree(resource.getInputStream());
        } catch (IOException ex) {
            throw new BizException(ERROR_CODE, year + " 年工作日历文件读取失败");
        }
    }

    private List<PublishWindow> readWindows(JsonNode windowsNode) {
        if (windowsNode == null || !windowsNode.isArray()) {
            return List.of();
        }
        List<PublishWindow> result = new ArrayList<>();
        for (JsonNode node : windowsNode) {
            LocalTime start = parseTime(node.path("start").asText(null));
            LocalTime end = parseTime(node.path("end").asText(null));
            LocalTime preferred = parseTime(node.path("preferredTime").asText(null));
            if (start == null || end == null || preferred == null) {
                continue;
            }
            PublishWindow normalized = normalizeWindow(new PublishWindow(
                    StringUtils.hasText(node.path("name").asText(null)) ? node.path("name").asText() : "custom",
                    start,
                    end,
                    preferred
            ));
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private PublishWindow normalizeWindow(PublishWindow window) {
        for (PublishWindow policy : DEFAULT_WINDOWS) {
            if (!policy.name().equalsIgnoreCase(window.name())) {
                continue;
            }
            return policy;
        }
        return window;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalTime.parse(value.trim());
    }

    public record BusinessDay(LocalDate date,
                              int type,
                              String dayName,
                              int week,
                              boolean adjustedWorkday,
                              List<PublishWindow> windows) {
    }

    public record PublishWindow(String name,
                                LocalTime start,
                                LocalTime end,
                                LocalTime preferredTime) {
    }

    public record PublishSlot(LocalDate date,
                              String windowName,
                              LocalTime windowStart,
                              LocalTime windowEnd,
                              java.time.LocalDateTime plannedAt,
                              int dayType,
                              String dayName,
                              int week,
                              boolean adjustedWorkday) {
    }
}
