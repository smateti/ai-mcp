package com.example.chat.llm;

import java.util.function.Consumer;

public interface ChatClient {
  String chatOnce(String userPrompt, double temperature, int maxTokens);

  /**
   * Stream LLM response token by token
   * @param userPrompt The prompt to send
   * @param temperature Temperature setting (0.0 to 1.0)
   * @param maxTokens Maximum tokens to generate
   * @param onToken Callback invoked for each token received
   */
  void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken);
}
