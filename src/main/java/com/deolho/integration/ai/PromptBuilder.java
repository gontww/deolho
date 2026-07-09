package com.deolho.integration.ai;

import com.deolho.domain.entity.ErrorRecord;

/**
 * Builds structured prompts for AI error analysis.
 */
public class PromptBuilder {

    private PromptBuilder() {
    }

    /**
     * Build the analysis prompt for a given error and requested response language.
     */
    public static String buildAnalysisPrompt(ErrorRecord error, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise a seguinte exceção Java.\n\n");

        sb.append(String.format(
                "IMPORTANTE: O resultado da sua análise nos campos 'summary', 'cause' e 'solution' DEVE ser escrito inteiramente no idioma/linguagem: %s.%n%n",
                language));

        sb.append("**Exceção:** ").append(error.getException()).append("\n");
        sb.append("**Mensagem:** ").append(error.getMessage()).append("\n");

        if (error.getStacktrace() != null && !error.getStacktrace().isBlank()) {
            // Limit stacktrace to first 30 lines to avoid token waste
            String[] lines = error.getStacktrace().split("\n");
            int limit = Math.min(lines.length, 30);
            sb.append("**Stack Trace (primeiras ").append(limit).append(" linhas):**\n```\n");
            for (int i = 0; i < limit; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("```\n");
        }

        if (error.getHost() != null) {
            sb.append("**Host:** ").append(error.getHost()).append("\n");
        }

        if (error.getApplication() != null) {
            sb.append("**Aplicação:** ").append(error.getApplication().getName()).append("\n");
            sb.append("**Ambiente:** ").append(error.getApplication().getEnvironment()).append("\n");
        }

        sb.append("\nInforme no seguinte formato JSON (sem markdown, apenas JSON puro):\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"Resumo breve do erro\",\n");
        sb.append("  \"cause\": \"Causa provável detalhada\",\n");
        sb.append("  \"solution\": \"Possíveis soluções e boas práticas\",\n");
        sb.append(
                "  \"category\": \"Categoria (ex: Database, Network, NullPointer, Authentication, IO, Configuration, Memory, Concurrency)\",\n");
        sb.append("  \"severity\": \"LOW | MEDIUM | HIGH | CRITICAL\",\n");
        sb.append("  \"confidence\": 0.85\n");
        sb.append("}\n");

        return sb.toString();
    }
}
