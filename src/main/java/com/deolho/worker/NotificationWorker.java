package com.deolho.worker;

import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.entity.Notification;
import com.deolho.domain.enums.JobType;
import com.deolho.domain.enums.NotificationType;
import com.deolho.domain.repository.ErrorRepository;
import com.deolho.domain.repository.NotificationRepository;
import com.deolho.queue.Job;
import com.deolho.queue.Worker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Sends notifications when new errors are detected.
 * Supports webhook notifications (initial implementation).
 * Future: Email, Discord, Slack, Teams.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWorker implements Worker {

    private final ErrorRepository errorRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Value("${deolho.notifications.enabled:false}")
    private boolean notificationsEnabled;

    @Value("${deolho.notifications.webhook-url:}")
    private String webhookUrl;

    @Override
    public JobType getJobType() {
        return JobType.NOTIFY;
    }

    @Override
    public void process(Job job) throws Exception {
        if (!notificationsEnabled) {
            log.debug("Notifications disabled. Skipping.");
            return;
        }

        PersistWorker.ErrorPayload payload = objectMapper.readValue(
                job.payload(), PersistWorker.ErrorPayload.class);

        ErrorRecord errorRecord = errorRepository.findById(payload.errorId())
                .orElseThrow(() -> new IllegalStateException("Error not found: id=" + payload.errorId()));

        // Send webhook notification
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            sendWebhookNotification(errorRecord);
        }
    }

    private void sendWebhookNotification(ErrorRecord errorRecord) {
        Notification notification = Notification.builder()
                .errorRecord(errorRecord)
                .type(NotificationType.WEBHOOK)
                .build();

        try {
            Map<String, Object> webhookPayload = Map.of(
                    "event", "new_error",
                    "errorId", errorRecord.getId(),
                    "application", errorRecord.getApplication().getName(),
                    "exception", errorRecord.getException(),
                    "message", errorRecord.getMessage(),
                    "severity", errorRecord.getSeverity().name(),
                    "host", errorRecord.getHost() != null ? errorRecord.getHost() : "unknown",
                    "timestamp", errorRecord.getFirstSeen().toString()
            );

            String jsonBody = objectMapper.writeValueAsString(webhookPayload);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                notification.markSent();
                log.info("Webhook notification sent for error id={}", errorRecord.getId());
            } else {
                notification.markFailed("HTTP " + response.statusCode() + ": " + response.body());
                log.warn("Webhook notification failed for error id={}: HTTP {}",
                        errorRecord.getId(), response.statusCode());
            }
        } catch (Exception e) {
            notification.markFailed(e.getMessage());
            log.error("Webhook notification error for error id={}: {}", errorRecord.getId(), e.getMessage());
        }

        notificationRepository.save(notification);
    }
}
