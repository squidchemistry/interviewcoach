package com.aicoach.common.web;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.ProblemDetailWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers boundary filters ahead of Spring Security (which runs at order -100), so that request ids
 * exist for every log line and oversized bodies are rejected before any further work.
 */
@Configuration(proxyBeanMethods = false)
public class WebFilterConfig {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(
            AppProperties properties, ProblemDetailWriter problemWriter) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodyLimitFilter(properties.http().maxBodyBytes(), problemWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
