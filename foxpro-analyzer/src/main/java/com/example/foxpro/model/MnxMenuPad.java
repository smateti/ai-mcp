package com.example.foxpro.model;

/**
 * Represents a menu pad (top-level menu bar item) in an MNX menu.
 */
public class MnxMenuPad {

    private final String name;
    private final String prompt;
    private String skipFor;
    private String message;

    public MnxMenuPad(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSkipFor() {
        return skipFor;
    }

    public void setSkipFor(String skipFor) {
        this.skipFor = skipFor;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "MenuPad{" + prompt + "}";
    }
}
