package com.naag.rag.repository;

import com.naag.rag.entity.FaqSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaqSettingsRepository extends JpaRepository<FaqSettings, String> {

    /**
     * Get the default settings (singleton)
     */
    default FaqSettings getSettings() {
        return findById("default").orElse(FaqSettings.createDefault());
    }
}
