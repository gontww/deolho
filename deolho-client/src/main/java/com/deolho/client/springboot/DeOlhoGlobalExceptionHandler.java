package com.deolho.client.springboot;

import com.deolho.client.DeOlhoClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * ControllerAdvice interceptor that catches any unhandled exceptions in the
 * Spring Web controllers and auto-reports them to DeOlho.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE) // Run after any application-specific handlers
public class DeOlhoGlobalExceptionHandler {

    private final DeOlhoClientProperties properties;

    public DeOlhoGlobalExceptionHandler(DeOlhoClientProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(Throwable.class)
    public void handleException(Throwable throwable) throws Throwable {
        if (properties.isEnabled()) {
            DeOlhoClient.report(
                properties.getServerUrl(),
                properties.getApplicationName(),
                properties.getEnvironment(),
                throwable
            );
        }
        // Rethrow the exception to let the default web response pipeline render it
        throw throwable;
    }
}
