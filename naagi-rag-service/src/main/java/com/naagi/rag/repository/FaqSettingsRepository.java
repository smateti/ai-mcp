package com.naagi.rag.repository;

import com.naagi.rag.entity.FaqSettings;
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
