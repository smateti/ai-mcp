package com.example.conjur.web.client.model;

public class AppRegistration {

    private String name;
    private String hostId;
    private String apiKey;
    private String message;
    private String error;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
