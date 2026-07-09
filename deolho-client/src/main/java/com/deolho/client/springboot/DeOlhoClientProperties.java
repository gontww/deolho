package com.deolho.client.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deolho")
public class DeOlhoClientProperties {

    /**
     * URL of the DeOlho monitoring server (default http://localhost:8888/api/v1/events).
     */
    private String serverUrl = "http://localhost:8888/api/v1/events";

    /**
     * Name of the application to identify in the dashboard.
     */
    private String applicationName = "unnamed-app";

    /**
     * Environment code (e.g. development, staging, production).
     */
    private String environment = "development";

    /**
     * Enable/disable automatic exception reporting.
     */
    private boolean enabled = true;

    // Getters and Setters
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
