package com.akrcode.akr.configure;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * CustomDataSource Class - Dynamic database based on login or condition.
 * Supports switching between local and Render DB using spring.datasource.url as base.
 * 
 * @author akash
 */
@Configuration
public class CustomDataSource {

    @Value("${spring.datasource.url}")
    private String baseUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public HikariDataSource dynamicDatabaseChange(String databaseName) throws SQLException {
        HikariConfig config = new HikariConfig();

        // Replace the last part (database name) with the provided databaseName
        String[] parts = baseUrl.split("/");
        parts[parts.length - 1] = databaseName;

        // Rebuild the URL and preserve any query params like ?sslmode=require
        String newUrl = String.join("/", parts);

        // If base URL has query params, like ?sslmode=require, append them
        if (baseUrl.contains("?")) {
            String queryParams = baseUrl.substring(baseUrl.indexOf("?"));
            newUrl += queryParams;
        }

        config.setJdbcUrl(newUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);

        return new HikariDataSource(config);
    }

    public String getDatabaseName() {
        String[] parts = baseUrl.split("/");
        String dbNamePart = parts[parts.length - 1];
        if (dbNamePart.contains("?")) {
            dbNamePart = dbNamePart.split("\\?")[0]; // remove query params
        }
        return dbNamePart;
    }
}
