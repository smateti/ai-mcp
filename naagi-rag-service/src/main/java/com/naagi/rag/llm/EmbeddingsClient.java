package com.naagi.rag.llm;

import java.util.List;

public interface EmbeddingsClient {
    List<Double> embed(String text);
}
