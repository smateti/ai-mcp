package com.example.conjur.admin.model;

import java.util.List;

public class BulkDatabaseRequest {

    private List<String> databases;

    public List<String> getDatabases() { return databases; }
    public void setDatabases(List<String> databases) { this.databases = databases; }
}
