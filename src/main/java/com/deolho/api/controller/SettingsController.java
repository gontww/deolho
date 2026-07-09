package com.deolho.api.controller;

import com.deolho.domain.entity.SystemSetting;
import com.deolho.domain.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller to fetch and update application-wide settings dynamically.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemSettingRepository settingsRepository;

    @GetMapping
    public ResponseEntity<List<SystemSetting>> getSettings() {
        List<SystemSetting> settings = settingsRepository.findAll().stream()
                .map(setting -> {
                    SystemSetting copy = new SystemSetting();
                    copy.setKey(setting.getKey());
                    copy.setDescription(setting.getDescription());
                    // Mask API keys for safety
                    if (setting.getKey().contains("api-key") && setting.getValue() != null && !setting.getValue().isBlank()) {
                        copy.setValue("*****");
                    } else {
                        copy.setValue(setting.getValue());
                    }
                    return copy;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(settings);
    }

    @PostMapping
    public ResponseEntity<Void> updateSettings(@RequestBody Map<String, String> updates) {
        log.info("Updating system settings: {}", updates.keySet());
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();

            settingsRepository.findById(key).ifPresent(setting -> {
                // If it is masked and wasn't edited, skip
                if (key.contains("api-key") && "*****".equals(newValue)) {
                    return;
                }
                setting.setValue(newValue != null ? newValue.trim() : "");
                settingsRepository.save(setting);
                log.info("Updated setting: {} = {}", key, key.contains("api-key") ? "*****" : newValue);
            });
        }
        return ResponseEntity.ok().build();
    }
}
