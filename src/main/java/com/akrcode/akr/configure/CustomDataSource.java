package com.akrcode.akr.configure;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;

/**
 * CustomDataSource - Reuses Hikari pools per DB and avoids connection overflow.
 *
 * Author: Akash
 */
@Configuration
public class CustomDataSource {

    @Value("${spring.datasource.url}")
    private String baseUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // Stores Hikari pools for each DB
    private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * Gets or creates a HikariDataSource for the specified DB.
     */
    public HikariDataSource dynamicDatabaseChange(String databaseName) throws SQLException {
        if (dataSourceMap.containsKey(databaseName)) {
            HikariDataSource existing = dataSourceMap.get(databaseName);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
        }

        // New pool config
        String jdbcUrl = buildNewJdbcUrl(databaseName);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(5);
        config.setPoolName("DynamicPool-" + databaseName);

        HikariDataSource newDataSource = new HikariDataSource(config);
        dataSourceMap.put(databaseName, newDataSource);
        return newDataSource;
    }

    /**
     * Builds a new JDBC URL by replacing the DB name in baseUrl.
     */
    private String buildNewJdbcUrl(String newDbName) {
        String urlWithoutParams = baseUrl;
        String queryParams = "";

        if (baseUrl.contains("?")) {
            urlWithoutParams = baseUrl.substring(0, baseUrl.indexOf("?"));
            queryParams = baseUrl.substring(baseUrl.indexOf("?"));
        }

        String[] parts = urlWithoutParams.split("/");
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid JDBC URL: " + baseUrl);
        }

        parts[parts.length - 1] = newDbName; // Replace DB name
        return String.join("/", parts) + queryParams;
    }

    /**
     * Gets the default database name from baseUrl.
     */
    public String getDatabaseName() {
        String urlWithoutParams = baseUrl;
        if (baseUrl.contains("?")) {
            urlWithoutParams = baseUrl.substring(0, baseUrl.indexOf("?"));
        }

        String[] parts = urlWithoutParams.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Gracefully closes all data sources on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        for (HikariDataSource ds : dataSourceMap.values()) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
        dataSourceMap.clear();
    }
}
