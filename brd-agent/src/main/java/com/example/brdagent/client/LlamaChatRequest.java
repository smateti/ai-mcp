package com.example.brdagent.client;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

// PROVENANCE: LlamaClient DTOs — agent loop step 2, request to /v1/chat/completions

/**
 * Request body for the OpenAI-compatible chat completions endpoint.
 */
public class LlamaChatRequest {

    private String model;
    private List<LlamaMessage> messages;
    private double temperature;

    @JsonbProperty("top_p")
    private double topP;

    @JsonbProperty("max_tokens")
    private int maxTokens;

    @JsonbProperty("response_format")
    private ResponseFormat responseFormat;

    public LlamaChatRequest() {
    }

    public LlamaChatRequest(String model, List<LlamaMessage> messages,
                            double temperature, double topP, int maxTokens,
                            boolean jsonMode) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        if (jsonMode) {
            this.responseFormat = new ResponseFormat("json_object");
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<LlamaMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<LlamaMessage> messages) {
        this.messages = messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    /**
     * Controls structured output mode. {@code {"type":"json_object"}} enables JSON mode.
     */
    public static class ResponseFormat {

        private String type;

        public ResponseFormat() {
        }

        public ResponseFormat(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
