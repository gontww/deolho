package com.deolho.domain.repository;

import com.deolho.domain.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    default String findValueByKeyOrDefault(String key, String defaultValue) {
        return findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    default boolean existsByKey(String key) {
        return existsById(key);
    }
}
