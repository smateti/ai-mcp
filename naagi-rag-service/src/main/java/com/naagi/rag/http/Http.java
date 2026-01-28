package com.naagi.rag.http;

import java.net.http.HttpClient;
import java.time.Duration;

public final class Http {
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private Http() {}
}
