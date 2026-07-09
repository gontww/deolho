package com.deolho.config;

import com.deolho.domain.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Dynamically binds the embedded server port using the 'server.port'
 * setting stored in the SQLite database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    private final SystemSettingRepository settingsRepository;

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        try {
            String portStr = settingsRepository.findValueByKeyOrDefault("server.port", "8888");
            int port = Integer.parseInt(portStr.trim());
            log.info("PortCustomizer: Configurando porta do servidor Tomcat para: {}", port);
            factory.setPort(port);
        } catch (Exception e) {
            log.warn("PortCustomizer: Falha ao ler a porta dinâmica da base de dados. Usando padrão.", e);
        }
    }
}
