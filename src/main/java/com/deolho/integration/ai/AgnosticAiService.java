package com.deolho.integration.ai;

import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.Severity;
import com.deolho.domain.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Agnostic AI service implementation supporting OpenAI, Google Gemini, and Anthropic Claude natively
 * as well as any OpenAI-compatible API endpoint (e.g. Groq, Ollama, DeepSeek, LocalAI).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgnosticAiService implements AiService {

    private final SystemSettingRepository settingsRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public AiAnalysisResult analyze(ErrorRecord errorRecord) {
        boolean aiEnabled = Boolean.parseBoolean(settingsRepository.findValueByKeyOrDefault("ai.enabled", "true"));
        String provider = settingsRepository.findValueByKeyOrDefault("ai.provider", "OPENAI").toUpperCase().trim();
        String language = settingsRepository.findValueByKeyOrDefault("ai.language", "pt-BR");

        if (!aiEnabled || "HEURISTIC".equalsIgnoreCase(provider)) {
            log.info("AI analysis is disabled or set to HEURISTIC. Running local fallback analysis for error id={}", errorRecord.getId());
            return heuristicAnalysis(errorRecord, language);
        }

        try {
            String apiKey = settingsRepository.findValueByKeyOrDefault("ai.api-key", "");
            String baseUrl = settingsRepository.findValueByKeyOrDefault("ai.base-url", "https://api.openai.com");
            String model = settingsRepository.findValueByKeyOrDefault("ai.model", "").trim();

            String prompt = PromptBuilder.buildAnalysisPrompt(errorRecord, language);
            HttpRequest request;

            if ("GEMINI".equals(provider)) {
                if (model.isEmpty()) {
                    model = "gemini-1.5-flash";
                }
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

                ObjectNode payload = objectMapper.createObjectNode();
                ArrayNode contents = objectMapper.createArrayNode();
                ObjectNode contentObj = objectMapper.createObjectNode();
                ArrayNode parts = objectMapper.createArrayNode();
                ObjectNode partObj = objectMapper.createObjectNode();
                partObj.put("text", prompt);
                parts.add(partObj);
                contentObj.set("parts", parts);
                contents.add(contentObj);
                payload.set("contents", contents);

                ObjectNode genConfig = objectMapper.createObjectNode();
                genConfig.put("temperature", 0.3);
                payload.set("generationConfig", genConfig);

                String requestBody = objectMapper.writeValueAsString(payload);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

            } else if ("CLAUDE".equals(provider)) {
                if (model.isEmpty()) {
                    model = "claude-3-5-sonnet-20241022";
                }
                String url = "https://api.anthropic.com/v1/messages";

                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("model", model);
                payload.put("max_tokens", 2048);
                payload.put("temperature", 0.3);

                ArrayNode messages = objectMapper.createArrayNode();
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.add(userMsg);
                payload.set("messages", messages);

                String requestBody = objectMapper.writeValueAsString(payload);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

            } else { // OPENAI or OPENAI_COMPATIBLE
                if (model.isEmpty()) {
                    model = "gpt-4o-mini";
                }
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                String url = baseUrl + "/v1/chat/completions";

                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("model", model);
                payload.put("temperature", 0.3);

                ArrayNode messages = objectMapper.createArrayNode();
                ObjectNode userMessage = objectMapper.createObjectNode();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);
                payload.set("messages", messages);

                String requestBody = objectMapper.writeValueAsString(payload);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                if (apiKey != null && !apiKey.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }
                request = requestBuilder.build();
            }

            log.info("Sending agnostic AI request to provider: {} (Model: {})", provider, model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String responseBody = response.body();
                log.debug("Agnostic AI raw response from {}: {}", provider, responseBody);
                return parseResponseBody(responseBody, provider, errorRecord);
            } else {
                log.error("AI HTTP Request to {} failed with status code: {}. Response: {}", provider, response.statusCode(), response.body());
                return heuristicAnalysis(errorRecord, language);
            }

        } catch (Exception e) {
            log.error("AI analysis failed for error id={}. Falling back to heuristic: {}",
                    errorRecord.getId(), e.getMessage(), e);
            return heuristicAnalysis(errorRecord, language);
        }
    }

    /**
     * Parse the response JSON body depending on the selected provider.
     */
    private AiAnalysisResult parseResponseBody(String jsonResponse, String provider, ErrorRecord errorRecord) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if ("GEMINI".equals(provider)) {
                JsonNode candidates = root.get("candidates");
                if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode contentNode = candidates.get(0).get("content");
                    if (contentNode != null) {
                        JsonNode parts = contentNode.get("parts");
                        if (parts != null && parts.isArray() && !parts.isEmpty()) {
                            String text = parts.get(0).get("text").asText();
                            return parseContent(text, errorRecord);
                        }
                    }
                }
            } else if ("CLAUDE".equals(provider)) {
                JsonNode contentNode = root.get("content");
                if (contentNode != null && contentNode.isArray() && !contentNode.isEmpty()) {
                    JsonNode textNode = contentNode.get(0);
                    if (textNode != null && textNode.has("text")) {
                        String text = textNode.get("text").asText();
                        return parseContent(text, errorRecord);
                    }
                }
            } else { // OPENAI or OPENAI_COMPATIBLE
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        String content = messageNode.get("content").asText();
                        return parseContent(content, errorRecord);
                    }
                }
            }
            throw new IllegalArgumentException("Invalid response structure from " + provider);
        } catch (Exception e) {
            log.warn("Failed to parse AI response structure from {}: {}. Using response body as fallback.", provider, e.getMessage());
            return new AiAnalysisResult(
                    "Parsing falhou para a resposta da IA",
                    "Falha ao ler o formato da resposta do endpoint " + provider,
                    jsonResponse.length() > 500 ? jsonResponse.substring(0, 500) : jsonResponse,
                    "Uncategorized",
                    errorRecord.getSeverity(),
                    0.3
            );
        }
    }

    private AiAnalysisResult parseContent(String content, ErrorRecord errorRecord) {
        try {
            String json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```(json)?\\s*", "").replaceFirst("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(json);

            String summary = node.has("summary") ? node.get("summary").asText() : "Análise indisponível";
            String cause = node.has("cause") ? node.get("cause").asText() : "Causa não identificada";
            String solution = node.has("solution") ? node.get("solution").asText() : "Solução não disponível";
            String category = node.has("category") ? node.get("category").asText() : "Uncategorized";
            Severity severity = parseSeverity(node.has("severity") ? node.get("severity").asText() : null);
            double confidence = node.has("confidence") ? node.get("confidence").asDouble(0.5) : 0.5;

            return new AiAnalysisResult(summary, cause, solution, category, severity, confidence);

        } catch (Exception e) {
            log.warn("Failed to parse JSON content from chat assistant. Using raw text: {}", e.getMessage());
            return new AiAnalysisResult(
                    content.length() > 500 ? content.substring(0, 500) : content,
                    "Parsing da resposta da IA falhou",
                    content,
                    "Uncategorized",
                    errorRecord.getSeverity(),
                    0.3
            );
        }
    }

    /**
     * Local heuristic analysis when AI is disabled/failed.
     */
    private AiAnalysisResult heuristicAnalysis(ErrorRecord errorRecord, String language) {
        String exception = errorRecord.getException().toLowerCase();
        boolean isEnglish = language != null && language.toLowerCase().startsWith("en");

        String category;
        String cause;
        String solution;
        Severity severity;

        if (exception.contains("sql") || exception.contains("jdbc") || exception.contains("hibernate")
                || exception.contains("database") || exception.contains("datasource")) {
            category = "Database";
            cause = isEnglish ? "Database or connection pool related issue" : "Problema relacionado ao banco de dados ou conexão";
            solution = isEnglish ? "Verify connection pool status, credentials and network latency." : "Verifique a configuração do datasource, pool de conexões (HikariCP) e o estado do banco de dados.";
            severity = Severity.HIGH;
        } else if (exception.contains("nullpointer")) {
            category = "NullPointer";
            cause = isEnglish ? "Null reference being dereferenced" : "Referência nula sendo acessada";
            solution = isEnglish ? "Check null conditions, use Optional class, and review instance initialization." : "Adicione verificações de null, use Optional, e revise a lógica de inicialização.";
            severity = Severity.MEDIUM;
        } else if (exception.contains("timeout") || exception.contains("connect")) {
            category = "Network";
            cause = isEnglish ? "Network timeout or connection failed to external API" : "Timeout ou falha de conexão com serviço externo";
            solution = isEnglish ? "Verify external API status, connection limits, and configure circuit breakers." : "Verifique a disponibilidade do serviço, configure timeouts adequados e implemente circuit breaker.";
            severity = Severity.HIGH;
        } else if (exception.contains("outofmemory") || exception.contains("heap")) {
            category = "Memory";
            cause = isEnglish ? "JVM out of memory limit" : "Consumo excessivo de memória";
            solution = isEnglish ? "Profile the JVM to check memory leaks, allocate more Heap space if needed." : "Aumente o heap da JVM, identifique memory leaks com profiler, e revise alocações de objetos.";
            severity = Severity.CRITICAL;
        } else if (exception.contains("security") || exception.contains("auth") || exception.contains("access")) {
            category = "Authentication";
            cause = isEnglish ? "Authentication or access control failure" : "Falha de autenticação ou autorização";
            solution = isEnglish ? "Check credentials, secure headers, tokens, and role mappings." : "Verifique credenciais, tokens e configurações de segurança.";
            severity = Severity.HIGH;
        } else if (exception.contains("io") || exception.contains("file") || exception.contains("stream")) {
            category = "IO";
            cause = isEnglish ? "File or streaming input/output error" : "Erro de entrada/saída em arquivo ou stream";
            solution = isEnglish ? "Check write permissions, file paths, and storage volume space." : "Verifique permissões, existência do arquivo e espaço em disco.";
            severity = Severity.MEDIUM;
        } else if (exception.contains("concurrent") || exception.contains("deadlock") || exception.contains("thread")) {
            category = "Concurrency";
            cause = isEnglish ? "Concurrent modification exception or deadlock warning" : "Problema de concorrência ou deadlock";
            solution = isEnglish ? "Review concurrency scopes, synchronization locks, and use thread-safe components." : "Revise sincronização, use locks adequados e considere estruturas thread-safe.";
            severity = Severity.HIGH;
        } else {
            category = "General";
            cause = isEnglish ? "Undetermined cause via local heuristics" : "Causa não identificada por análise heurística";
            solution = isEnglish ? "Inspect stacktrace and error details for manual diagnosis." : "Revise o stacktrace e a mensagem de erro para diagnóstico manual.";
            severity = errorRecord.getSeverity();
        }

        return new AiAnalysisResult(
                (isEnglish ? "Heuristic analysis: " : "Análise heurística: ") + errorRecord.getException(),
                cause,
                solution,
                category,
                severity,
                0.4
        );
    }

    private Severity parseSeverity(String value) {
        if (value == null) return Severity.MEDIUM;
        try {
            return Severity.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }
}
