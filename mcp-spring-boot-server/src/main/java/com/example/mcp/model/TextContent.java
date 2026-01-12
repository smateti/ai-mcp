package com.example.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextContent {
    private String type = "text";
    private String text;

    public TextContent(String text) {
        this.text = text;
    }
}
