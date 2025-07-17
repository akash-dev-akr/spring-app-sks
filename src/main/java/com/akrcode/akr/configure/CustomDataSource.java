package com.akrcode.akr.configure;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * CustomDataSource Class This Class defines----Dynamic database based on login
 * 
 * @author akash
 *
 */

@Configuration
public class CustomDataSource {
	@Value("${spring.datasource.url}")
	private String databaseUrl;

	public HikariDataSource dynamicDatabaseChange(String DatabaseName) throws SQLException {

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:postgresql://localhost:5432/" + DatabaseName);
		config.setUsername("postgres");
		config.setPassword("admin");

		HikariDataSource dataSource = new HikariDataSource(config);

		return dataSource;

	}

	public String getDatabaseName() {
		String[] parts = databaseUrl.split("/");
		String databaseName = parts[parts.length - 1];
		return databaseName;
	}
}
