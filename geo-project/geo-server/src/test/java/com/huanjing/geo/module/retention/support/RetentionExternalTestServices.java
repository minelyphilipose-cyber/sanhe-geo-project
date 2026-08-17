package com.huanjing.geo.module.retention.support;

import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.util.UUID;

public final class RetentionExternalTestServices {
    private static final String MYSQL_URL = "RETENTION_MYSQL_IT_SERVER_URL";
    private static final String MYSQL_USER = "RETENTION_MYSQL_IT_USER";
    private static final String MYSQL_PASSWORD = "RETENTION_MYSQL_IT_PASSWORD";
    private static final String REDIS_HOST = "RETENTION_REDIS_IT_HOST";
    private static final String REDIS_PORT = "RETENTION_REDIS_IT_PORT";
    private static final String REDIS_USERNAME = "RETENTION_REDIS_IT_USERNAME";
    private static final String REDIS_PASSWORD = "RETENTION_REDIS_IT_PASSWORD";
    private static final String REDIS_DATABASE = "RETENTION_REDIS_IT_DATABASE";

    private RetentionExternalTestServices() {
    }

    public static boolean mysqlConfigured() {
        return hasText(System.getenv(MYSQL_URL));
    }

    public static boolean redisConfigured() {
        return hasText(System.getenv(REDIS_HOST));
    }

    public static ExternalMysql createMysql(String safePrefix) throws Exception {
        if (!safePrefix.matches("geo_retention_[a-z0-9_]+_")) {
            throw new IllegalArgumentException("Unsafe external retention test database prefix");
        }
        String serverUrl = require(MYSQL_URL);
        String username = environmentOrDefault(MYSQL_USER, "root");
        String password = environmentOrDefault(MYSQL_PASSWORD, "");
        String databaseName = safePrefix + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(serverUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String databaseUrl = databaseUrl(serverUrl, databaseName);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, username, password);
        return new ExternalMysql(serverUrl, username, password, databaseName, safePrefix, dataSource);
    }

    public static LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(require(REDIS_HOST));
        configuration.setPort(integerEnvironment(REDIS_PORT, 6379));
        configuration.setDatabase(integerEnvironment(REDIS_DATABASE, 15));
        String username = System.getenv(REDIS_USERNAME);
        if (hasText(username)) {
            configuration.setUsername(username.trim());
        }
        String password = System.getenv(REDIS_PASSWORD);
        if (hasText(password)) {
            configuration.setPassword(RedisPassword.of(password));
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static String databaseUrl(String serverUrl, String databaseName) {
        String withDatabase = serverUrl.replaceFirst("/\\?", "/" + databaseName + "?");
        if (!withDatabase.equals(serverUrl)) {
            return withDatabase;
        }
        String separator = serverUrl.contains("?") ? "&" : "?";
        withDatabase = serverUrl.endsWith("/") ? serverUrl + databaseName : serverUrl + "/" + databaseName;
        return withDatabase + separator
                + "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (!hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static int integerEnvironment(String name, int fallback) {
        String value = System.getenv(name);
        return hasText(value) ? Integer.parseInt(value.trim()) : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ExternalMysql(String serverUrl,
                                String username,
                                String password,
                                String databaseName,
                                String safePrefix,
                                DriverManagerDataSource dataSource) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (!databaseName.startsWith(safePrefix)) {
                throw new IllegalStateException("Refusing to drop unexpected external test database");
            }
            try (var connection = DriverManager.getConnection(serverUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            }
        }
    }
}
