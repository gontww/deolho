package com.deolho.config;

import com.deolho.domain.entity.SystemSetting;
import com.deolho.domain.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SettingsInitializer implements ApplicationListener<ContextRefreshedEvent> {

    private final SystemSettingRepository settingsRepository;

    @Value("${OPENAI_API_KEY:}")
    private String defaultApiKey;

    @Value("${server.port:8888}")
    private String defaultPort;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Initializing system settings database...");

        initSetting("server.port", defaultPort, "Porta TCP em que o servidor do DeOlho irá escutar");
        initSetting("ai.enabled", "true", "Habilita ou desabilita a integração com Inteligência Artificial");
        initSetting("ai.provider", "OPENAI", "Provedor de IA (OPENAI | HEURISTIC)");
        initSetting("ai.api-key", defaultApiKey, "Chave de API do provedor de IA (OpenAI, Groq, etc.)");
        initSetting("ai.base-url", "https://api.openai.com", "URL base da API (OpenAI compatível, ex: Groq, Ollama)");
        initSetting("ai.model", "gpt-4o-mini", "Modelo de Chat (ex: gpt-4o-mini, llama3, mixstral)");
        initSetting("ai.language", "pt-BR", "Idioma em que a IA deve gerar as respostas (ex: pt-BR, en-US, es-ES)");

        log.info("System settings database initialized.");
    }

    private void initSetting(String key, String value, String description) {
        if (!settingsRepository.existsByKey(key)) {
            SystemSetting setting = new SystemSetting();
            setting.setKey(key);
            setting.setValue(value != null ? value : "");
            setting.setDescription(description);
            settingsRepository.save(setting);
            log.info("Seeded setting: {} = {}", key, value);
        }
    }
}
