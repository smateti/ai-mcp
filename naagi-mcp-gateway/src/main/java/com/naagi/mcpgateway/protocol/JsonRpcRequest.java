package com.naagi.mcpgateway.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class JsonRpcRequest {
    private String jsonrpc = "2.0";
    private String method;
    private JsonNode params;
    private Object id;
}
