package com.deolho.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback Appender that automatically intercepts any logged errors
 * and forwards them to the DeOlho dashboard asynchronously.
 */
public class DeOlhoLogbackAppender extends AppenderBase<ILoggingEvent> {

    private String serverUrl = "http://localhost:8888/api/v1/events";
    private String application = "unnamed-app";
    private String environment = "development";

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getApplication() { return application; }
    public void setApplication(String application) { this.application = application; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || !isStarted()) {
            return;
        }

        // Only process ERROR level logs
        if (event.getLevel().toInt() >= ch.qos.logback.classic.Level.ERROR_INT) {
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                sendError(event, throwableProxy);
            } else {
                // If there's no exception, report the log message anyway
                sendErrorMessage(event);
            }
        }
    }

    private void sendError(ILoggingEvent event, IThrowableProxy throwableProxy) {
        try {
            // Build a synthetic exception representation
            String exceptionName = throwableProxy.getClassName();
            
            StringBuilder sb = new StringBuilder();
            formatThrowable(sb, throwableProxy);
            String stacktrace = sb.toString();

            // Run async reporting via DeOlhoClient core
            DeOlhoClient.report(serverUrl, application, environment, new SyntheticException(exceptionName, event.getFormattedMessage(), stacktrace));
        } catch (Exception e) {
            // Fail silently
        }
    }

    private void sendErrorMessage(ILoggingEvent event) {
        try {
            DeOlhoClient.report(serverUrl, application, environment, 
                new SyntheticException("LoggedError", event.getFormattedMessage(), "No stacktrace. Event logged at: " + event.getLoggerName()));
        } catch (Exception e) {
            // Fail silently
        }
    }

    private void formatThrowable(StringBuilder sb, IThrowableProxy tp) {
        if (tp == null) return;
        sb.append(tp.getClassName()).append(": ").append(tp.getMessage()).append("\n");
        for (StackTraceElementProxy step : tp.getStackTraceElementProxyArray()) {
            sb.append("\tat ").append(step.getSTEAsString()).append("\n");
        }
        if (tp.getCause() != null) {
            sb.append("Caused by: ");
            formatThrowable(sb, tp.getCause());
        }
    }

    /**
     * Synthetic lightweight helper class to map nested Logback trace details.
     */
    private static class SyntheticException extends Throwable {
        private final String className;
        private final String stacktrace;

        public SyntheticException(String className, String message, String stacktrace) {
            super(message);
            this.className = className;
            this.stacktrace = stacktrace;
        }

        @Override
        public String toString() {
            return className + ": " + getMessage();
        }

        @Override
        public void printStackTrace(java.io.PrintWriter s) {
            s.print(stacktrace);
        }
    }
}
