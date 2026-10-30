package com.example.booking.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AdminSqlConfig {

    @Bean
    public JdbcTemplate adminJdbcTemplate(
            @Value("${booking.admin-sql.jdbc-url}") String jdbcUrl,
            @Value("${booking.admin-sql.username:root}") String username,
            @Value("${booking.admin-sql.password:root}") String password) {

        DataSource dataSource = DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setMaxRows(50);

        return jdbcTemplate;
    }

}
