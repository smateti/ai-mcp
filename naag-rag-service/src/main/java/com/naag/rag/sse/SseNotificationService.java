package com.naag.rag.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseNotificationService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> uploadEmitters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeToUpload(String uploadId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        uploadEmitters.computeIfAbsent(uploadId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            var list = uploadEmitters.get(uploadId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    uploadEmitters.remove(uploadId);
                }
            }
        });

        emitter.onTimeout(() -> {
            var list = uploadEmitters.get(uploadId);
            if (list != null) {
                list.remove(emitter);
            }
        });

        emitter.onError(e -> {
            var list = uploadEmitters.get(uploadId);
            if (list != null) {
                list.remove(emitter);
            }
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("uploadId", uploadId, "message", "Subscribed to upload notifications")));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE event for upload {}", uploadId);
        }

        return emitter;
    }

    public SseEmitter subscribeToGlobal() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        globalEmitters.add(emitter);

        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Subscribed to global notifications")));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE event for global subscription");
        }

        return emitter;
    }

    public void notifyUploadProgress(String uploadId, UploadEvent event) {
        log.debug("Notifying upload progress: uploadId={}, event={}", uploadId, event.type());

        // Notify upload-specific subscribers
        var emitters = uploadEmitters.get(uploadId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.type())
                            .data(event.toMap()));
                } catch (IOException e) {
                    log.debug("Removing disconnected SSE emitter for upload {}: {}", uploadId, e.getMessage());
                    emitters.remove(emitter);
                    safeComplete(emitter);
                } catch (Exception e) {
                    log.debug("Error sending SSE event for upload {}: {}", uploadId, e.getMessage());
                    emitters.remove(emitter);
                    safeComplete(emitter);
                }
            }
        }

        // Notify global subscribers
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event.toMap()));
            } catch (IOException e) {
                log.debug("Removing disconnected global SSE emitter: {}", e.getMessage());
                globalEmitters.remove(emitter);
                safeComplete(emitter);
            } catch (Exception e) {
                log.debug("Error sending global SSE event: {}", e.getMessage());
                globalEmitters.remove(emitter);
                safeComplete(emitter);
            }
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Ignore errors when completing already-closed emitters
        }
    }

    public record UploadEvent(
            String type,
            String uploadId,
            String status,
            String message,
            int progress,
            int total,
            Map<String, Object> data
    ) {
        public static UploadEvent processing(String uploadId, String message, int progress, int total) {
            return new UploadEvent("processing", uploadId, "PROCESSING", message, progress, total, Map.of());
        }

        public static UploadEvent completed(String uploadId, String status, String message, Map<String, Object> data) {
            return new UploadEvent("completed", uploadId, status, message, 100, 100, data);
        }

        public static UploadEvent error(String uploadId, String message) {
            return new UploadEvent("error", uploadId, "FAILED", message, 0, 0, Map.of());
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "type", type,
                    "uploadId", uploadId,
                    "status", status,
                    "message", message,
                    "progress", progress,
                    "total", total,
                    "data", data
            );
        }
    }
}
