package com.example.brdagent.client;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

// PROVENANCE: LlamaClient DTOs — defensive response parsing (quality requirement)

/**
 * Response from the OpenAI-compatible chat completions endpoint.
 * Handles both string and array forms of the content field defensively.
 */
public class LlamaChatResponse {

    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    /**
     * Extracts the assistant's content string from the first choice.
     * Returns null if no choices or content is empty.
     */
    public String extractContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice first = choices.get(0);
        if (first.getMessage() == null) {
            return null;
        }
        return first.getMessage().getContent();
    }

    /**
     * Returns the finish reason for the first choice, or null.
     */
    public String extractFinishReason() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).getFinishReason();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public static class Choice {

        private int index;
        private LlamaMessage message;

        @JsonbProperty("finish_reason")
        private String finishReason;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public LlamaMessage getMessage() {
            return message;
        }

        public void setMessage(LlamaMessage message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    public static class Usage {

        @JsonbProperty("prompt_tokens")
        private int promptTokens;

        @JsonbProperty("completion_tokens")
        private int completionTokens;

        @JsonbProperty("total_tokens")
        private int totalTokens;

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
