package com.example.foxpro.model;

/**
 * Represents a single procedure or function within a FoxPro source file.
 */
public class FoxProProcedure {

    private final String name;
    private final String type; // PROCEDURE or FUNCTION
    private String comment;
    private String body;

    public FoxProProcedure(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return type + " " + name;
    }
}
