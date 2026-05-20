package com.example.foxpro.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with the Ollama REST API running locally.
 * Sends FoxPro code to Llama 3.1 for summarization.
 */
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final MediaType JSON_MEDIA = MediaType.get("application/json");

    private final String baseUrl;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * @param baseUrl Ollama API base URL (default: http://localhost:11434)
     * @param model   Model name (e.g., "llama3.1")
     */
    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.gson = new Gson();

        // Longer timeouts since LLM inference can take time, especially for large code blocks
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)  // 5 min for large responses
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Default constructor using localhost:11434 and llama3.1 model.
     */
    public OllamaClient() {
        this("http://localhost:11434", "llama3.1");
    }

    /**
     * Sends a prompt to the Ollama API and returns the generated response.
     *
     * @param prompt The full prompt to send to the model
     * @return The model's text response
     */
    public String generate(String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);

        // Optional: set generation parameters
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.3);       // Lower temp for more factual summaries
        options.addProperty("num_predict", 2048);      // Max tokens in response
        options.addProperty("top_p", 0.9);
        requestBody.add("options", options);

        String jsonBody = gson.toJson(requestBody);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/generate")
                .post(RequestBody.create(jsonBody, JSON_MEDIA))
                .build();

        logger.debug("Sending request to Ollama ({} model)...", model);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No body";
                throw new IOException("Ollama API error (HTTP " + response.code() + "): " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            if (jsonResponse.has("response")) {
                return jsonResponse.get("response").getAsString();
            } else {
                throw new IOException("Unexpected response format: " + responseBody);
            }
        }
    }

    /**
     * Summarizes a FoxPro code block by building an appropriate prompt.
     *
     * @param codeBlock The FoxPro source code to analyze
     * @param context   Additional context (e.g., file name, module description)
     * @return The generated summary
     */
    public String summarizeFoxProCode(String codeBlock, String context) throws IOException {
        String prompt = buildSummarizationPrompt(codeBlock, context);
        return generate(prompt);
    }

    /**
     * Builds a structured prompt for FoxPro code summarization.
     */
    private String buildSummarizationPrompt(String code, String context) {
        return """
                You are a software analyst expert in Visual FoxPro (VFP) applications.
                Analyze the following FoxPro source code and provide a structured summary.

                Context: %s

                Please provide:
                1. **Use Case / Purpose**: What business function does this code serve?
                2. **Key Operations**: What are the main operations performed (CRUD, calculations, validations)?
                3. **Data Tables Used**: Which database tables (.dbf) are accessed and how?
                4. **Business Rules**: What validation rules or business logic is enforced?
                5. **User Interactions**: What inputs does it require and what outputs/messages does it produce?
                6. **Dependencies**: What other modules/procedures does it call?

                FoxPro Source Code:
                ```foxpro
                %s
                ```

                Provide a clear, concise summary suitable for documentation purposes.
                """.formatted(context, code);
    }

    /**
     * Tests connectivity to the Ollama server.
     *
     * @return true if server is reachable and model is available
     */
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/tags")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    logger.info("Ollama server reachable. Available models: {}", body);
                    return body.contains(model);
                }
            }
        } catch (IOException e) {
            logger.error("Cannot connect to Ollama at {}: {}", baseUrl, e.getMessage());
        }
        return false;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
