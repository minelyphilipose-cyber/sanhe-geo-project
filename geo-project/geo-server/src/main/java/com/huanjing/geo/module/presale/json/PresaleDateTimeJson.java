package com.huanjing.geo.module.presale.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 售前模块 {@link LocalDateTime} ↔ RFC3339 序列化工具。
 * <p>
 * <b>重要:这不是 Spring {@code @Configuration}。</b>本类只提供两个嵌套的 Serializer/Deserializer
 * 供 DTO 字段通过 {@code @JsonSerialize(using=...)} / {@code @JsonDeserialize(using=...)} 注解显式挂载,
 * 作用域限定在 presale 模块自己的 DTO 内。
 * </p>
 * <p>
 * <b>为什么不做全局配置:</b>仓库现状的 {@code spring.jackson.date-format: yyyy-MM-dd HH:mm:ss}
 * 只对 {@code java.util.Date} 生效,不影响 {@link LocalDateTime}。老接口目前依赖 Jackson
 * {@code JavaTimeModule} 的默认行为(输出 ISO 本地时间 {@code "2026-04-18T14:05:00"})。
 * 如果用 {@code Jackson2ObjectMapperBuilderCustomizer} 接管全局 LocalDateTime,会隐式把老接口
 * 的输出改成带偏移 {@code "...+08:00"},是一个波及全仓的 breaking change。
 * 字段级注解是 presale 模块不污染全局的唯一稳妥路径。
 * </p>
 * <p>
 * <b>契约目标:</b>DTO 层 Java 类型保留 {@code LocalDateTime}(对齐 MySQL DATETIME 无偏移量),
 * JSON 序列化格式对齐 schema v1.2 的 {@code format: date-time}(RFC3339),与 mock 样本
 * {@code "2026-04-18T14:05:00+08:00"} 一致,保证 schema 校验通过、前后端 mock/真实接口可互换。
 * </p>
 * <p>
 * <b>序列化:</b>LocalDateTime → 按固定业务时区 +08:00 挂上偏移 → RFC3339 字符串。
 * <br>
 * <b>反序列化:</b>宽松接受:
 * <ul>
 *   <li>RFC3339 带偏移:{@code 2026-04-18T14:05:00+08:00}(标准,前端下发使用)</li>
 *   <li>RFC3339 带 Z:{@code 2026-04-18T06:05:00Z}(转换为 +08:00)</li>
 *   <li>ISO 无偏移:{@code 2026-04-18T14:05:00}(兜底,视为 +08:00)</li>
 *   <li>MySQL 空格:{@code 2026-04-18 14:05:00}(JDBC 回读兜底,视为 +08:00)</li>
 * </ul>
 * </p>
 * <p>
 * <b>用法示例(DTO 字段):</b>
 * <pre>
 * &#64;JsonProperty("generated_at")
 * &#64;JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
 * &#64;JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
 * private LocalDateTime generatedAt;
 * </pre>
 * </p>
 * <p>
 * <b>作用域限制说明:</b>
 * <ul>
 *   <li>✅ 覆盖:通过 Spring MVC 返回 presale DTO 的场景(前端可见的所有响应)</li>
 *   <li>⚠️ 不覆盖:Redis 缓存(RedisConfig 自建 ObjectMapper,不继承 MVC 上下文;本项目 presale DTO 目前不进 Redis,未来若进需单独挂注解或扩展 RedisConfig)</li>
 *   <li>⚠️ 不覆盖:MyBatis 的 JSON TypeHandler(读写 JSON 列时不走 Jackson,走 MyBatis 自己的机制,需在 TypeHandler 内独立处理,或确保 JSON 列内的时间字段在业务层序列化时已挂注解)</li>
 * </ul>
 * 详见 README "跨序列化上下文注意事项"。
 * </p>
 *
 * @see com.huanjing.geo.module.presale.dto.snapshot.raw.RawMeta#generatedAt
 * @see com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail.NegativeEvidence#testedAt
 * @see com.huanjing.geo.module.presale.dto.snapshot.merged.MergedViewMeta
 */
public final class PresaleDateTimeJson {

    /** 业务统一时区:东八区。当前仅单区部署。 */
    public static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(8);

    /** 序列化输出格式:RFC3339 with offset,如 2026-04-18T14:05:00+08:00。 */
    public static final DateTimeFormatter RFC3339_WITH_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** MySQL JDBC 常见回读格式(空格分隔)。 */
    private static final DateTimeFormatter MYSQL_SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PresaleDateTimeJson() {
        // 工具类,禁止实例化
    }

    /**
     * LocalDateTime → RFC3339 with +08:00 offset。
     * 必须为 public,Jackson 通过 {@code @JsonSerialize(using=...)} 反射实例化。
     */
    public static class Serializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            OffsetDateTime odt = value.atOffset(BUSINESS_OFFSET);
            gen.writeString(odt.format(RFC3339_WITH_OFFSET));
        }
    }

    /**
     * 宽松反序列化:支持 RFC3339 带偏移 / 带 Z / 无偏移 / MySQL 空格格式。
     * 必须为 public,Jackson 通过 {@code @JsonDeserialize(using=...)} 反射实例化。
     */
    public static class Deserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isEmpty()) {
                return null;
            }

            // 优先 RFC3339 带偏移或 Z
            try {
                OffsetDateTime odt = OffsetDateTime.parse(text);
                // 统一归到 +08:00 的本地时间
                return odt.withOffsetSameInstant(BUSINESS_OFFSET).toLocalDateTime();
            } catch (DateTimeParseException ignore) {
                // fall through
            }

            // ISO 本地时间无偏移
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ignore) {
                // fall through
            }

            // MySQL 空格格式
            try {
                return LocalDateTime.parse(text, MYSQL_SPACE_FORMAT);
            } catch (DateTimeParseException ignore) {
                // fall through
            }

            throw new IOException("Unrecognized date-time format: " + text
                    + " (expected RFC3339 like 2026-04-18T14:05:00+08:00)");
        }
    }
}
