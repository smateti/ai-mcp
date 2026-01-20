package com.naag.rag.service;

import com.naag.rag.entity.FaqSettings;
import com.naag.rag.repository.FaqSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing FAQ runtime settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaqSettingsService {

    private final FaqSettingsRepository settingsRepository;

    /**
     * Get current FAQ settings
     */
    public FaqSettings getSettings() {
        return settingsRepository.getSettings();
    }

    /**
     * Check if FAQ query is enabled
     */
    public boolean isFaqQueryEnabled() {
        return getSettings().isFaqQueryEnabled();
    }

    /**
     * Check if user question storage is enabled
     */
    public boolean isStoreUserQuestionsEnabled() {
        return getSettings().isStoreUserQuestions();
    }

    /**
     * Get minimum similarity score for FAQ matching
     */
    public double getMinSimilarityScore() {
        return getSettings().getMinSimilarityScore();
    }

    /**
     * Get auto-select threshold for Q&A validation
     */
    public double getAutoSelectThreshold() {
        return getSettings().getAutoSelectThreshold();
    }

    /**
     * Update FAQ settings
     */
    @Transactional
    public FaqSettings updateSettings(FaqSettings settings, String updatedBy) {
        FaqSettings current = settingsRepository.findById("default")
                .orElse(FaqSettings.createDefault());

        current.setFaqQueryEnabled(settings.isFaqQueryEnabled());
        current.setMinSimilarityScore(settings.getMinSimilarityScore());
        current.setStoreUserQuestions(settings.isStoreUserQuestions());
        current.setAutoSelectThreshold(settings.getAutoSelectThreshold());
        current.setUpdatedBy(updatedBy);

        FaqSettings saved = settingsRepository.save(current);
        log.info("FAQ settings updated by {}: faqQueryEnabled={}, minSimilarityScore={}, storeUserQuestions={}",
                updatedBy, saved.isFaqQueryEnabled(), saved.getMinSimilarityScore(), saved.isStoreUserQuestions());

        return saved;
    }

    /**
     * Toggle FAQ query on/off
     */
    @Transactional
    public FaqSettings toggleFaqQuery(boolean enabled, String updatedBy) {
        FaqSettings current = settingsRepository.findById("default")
                .orElse(FaqSettings.createDefault());

        current.setFaqQueryEnabled(enabled);
        current.setUpdatedBy(updatedBy);

        FaqSettings saved = settingsRepository.save(current);
        log.info("FAQ query {} by {}", enabled ? "enabled" : "disabled", updatedBy);

        return saved;
    }

    /**
     * Initialize default settings if not exists
     */
    @Transactional
    public void initializeDefaultSettings() {
        if (!settingsRepository.existsById("default")) {
            settingsRepository.save(FaqSettings.createDefault());
            log.info("Initialized default FAQ settings");
        }
    }
}
