package com.deolho.client.springboot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that instantiates the DeOlho Exception Interceptor
 * automatically if deolho.enabled is set to true (or not specified).
 */
@AutoConfiguration
@EnableConfigurationProperties(DeOlhoClientProperties.class)
@ConditionalOnProperty(prefix = "deolho", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeOlhoClientAutoConfiguration {

    @Bean
    public DeOlhoGlobalExceptionHandler deOlhoGlobalExceptionHandler(DeOlhoClientProperties properties) {
        return new DeOlhoGlobalExceptionHandler(properties);
    }
}
