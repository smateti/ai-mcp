package com.example.brdagent.scanner;

// PROVENANCE: FoxProSourceScanner — input record for the agent loop

/**
 * Immutable representation of a FoxPro source file ready for extraction.
 *
 * @param path     relative path from source root (e.g. "programs/CUSTUPD.prg")
 * @param fileType normalised type tag sent to the LLM: prg, scx, frx, mnx, dbf-schema
 * @param content  full UTF-8 content of the file
 */
public record SourceFile(String path, String fileType, String content) {
}
