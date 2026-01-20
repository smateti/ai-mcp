package com.naag.rag.llm;

import java.util.function.Consumer;

public interface ChatClient {
    String chatOnce(String userPrompt, double temperature, int maxTokens);

    void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken);
}
