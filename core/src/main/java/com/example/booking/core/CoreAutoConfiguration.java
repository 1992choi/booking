package com.example.booking.core;

import com.example.booking.core.error.GlobalExceptionHandler;
import com.example.booking.core.logging.RequestLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
public class CoreAutoConfiguration {

    @Bean
    public PingService pingService() {
        return new PingService();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> bean = new FilterRegistrationBean<>(new RequestLoggingFilter());
        bean.addUrlPatterns("/*");
        // JwtAuthenticationFilter보다 먼저 실행되어 인증 실패도 로깅
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

}