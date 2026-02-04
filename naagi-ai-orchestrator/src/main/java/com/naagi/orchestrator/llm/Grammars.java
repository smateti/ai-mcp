package com.naagi.orchestrator.llm;

/**
 * GBNF grammar constants for llama.cpp structured output.
 * Grammar forces the LLM to produce output matching the defined format.
 *
 * Usage: pass as the "grammar" field in the llama.cpp API request body.
 */
public final class Grammars {

    private Grammars() {}

    /**
     * Forces output to be a valid JSON object: {"key": "value", ...}
     */
    public static final String JSON_OBJECT = """
            root ::= json-object
            json-object ::= "{" ws members? ws "}"
            members ::= pair (ws "," ws pair)*
            pair ::= ws string ws ":" ws value ws
            value ::= string | number | json-object | array | "true" | "false" | "null"
            array ::= "[" ws array-values? ws "]"
            array-values ::= value (ws "," ws value)*
            string ::= "\\"" characters "\\""
            characters ::= character*
            character ::= [^"\\\\] | "\\\\" escape-char
            escape-char ::= ["\\\\//bfnrt] | "u" hex hex hex hex
            hex ::= [0-9a-fA-F]
            number ::= integer fraction? exponent?
            integer ::= "-"? ("0" | [1-9] [0-9]*)
            fraction ::= "." [0-9]+
            exponent ::= [eE] [+-]? [0-9]+
            ws ::= [ \\t\\n]*
            """;
}
