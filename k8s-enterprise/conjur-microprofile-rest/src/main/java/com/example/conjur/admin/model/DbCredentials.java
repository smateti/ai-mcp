package com.example.conjur.admin.model;

public class DbCredentials {

    private String database;
    private String uidVariableId;
    private String pwdVariableId;
    private String username;
    private String password;
    private boolean found;
    private String error;

    public DbCredentials() {}

    public DbCredentials(String database) {
        this.database = database;
        this.uidVariableId = "db/" + database + "-uid";
        this.pwdVariableId = "db/" + database + "-pwd";
    }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUidVariableId() { return uidVariableId; }
    public void setUidVariableId(String uidVariableId) { this.uidVariableId = uidVariableId; }

    public String getPwdVariableId() { return pwdVariableId; }
    public void setPwdVariableId(String pwdVariableId) { this.pwdVariableId = pwdVariableId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
