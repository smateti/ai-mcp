package com.example.gitlabmigration.state;

import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.model.TransferStateEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Load/save transfer_state.json for Phase 1 resume support.
 * Uses atomic write (write to tmp file, then rename) to prevent corruption.
 */
@Component
public class TransferStateManager {

    private static final TypeReference<Map<String, TransferStateEntry>> STATE_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final Path stateFilePath;

    public TransferStateManager(MigrationProperties props, ObjectMapper mapper) {
        this.stateFilePath = Path.of(props.getStateFile());
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Load resume state from disk. Returns empty map if file does not exist. */
    public Map<String, TransferStateEntry> load() throws IOException {
        if (!Files.exists(stateFilePath)) {
            return new LinkedHashMap<>();
        }
        return mapper.readValue(stateFilePath.toFile(), STATE_TYPE);
    }

    /** Atomically persist resume state (write tmp file, then rename). */
    public void save(Map<String, TransferStateEntry> state) throws IOException {
        Path tmp = Path.of(stateFilePath + ".tmp");
        mapper.writeValue(tmp.toFile(), state);
        Files.move(tmp, stateFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
}
