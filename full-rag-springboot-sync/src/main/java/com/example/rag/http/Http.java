package com.example.rag.http;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared, thread-safe HttpClient. All calls are synchronous (blocking).
 */
public final class Http {
  public static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .version(HttpClient.Version.HTTP_1_1)
      .build();

  private Http() {}
}
