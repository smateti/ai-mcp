package com.example.conjur.web.client.model;

import java.util.List;

public class StatusInfo {

    private String conjurUrl;
    private String account;
    private String authnLogin;
    private boolean enabled;
    private boolean reachable;
    private boolean authenticated;
    private List<String> knownSecrets;
    private List<String> knownDatabases;
    private List<String> knownApps;

    public String getConjurUrl() { return conjurUrl; }
    public void setConjurUrl(String conjurUrl) { this.conjurUrl = conjurUrl; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getAuthnLogin() { return authnLogin; }
    public void setAuthnLogin(String authnLogin) { this.authnLogin = authnLogin; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    public List<String> getKnownSecrets() { return knownSecrets; }
    public void setKnownSecrets(List<String> knownSecrets) { this.knownSecrets = knownSecrets; }

    public List<String> getKnownDatabases() { return knownDatabases; }
    public void setKnownDatabases(List<String> knownDatabases) { this.knownDatabases = knownDatabases; }

    public List<String> getKnownApps() { return knownApps; }
    public void setKnownApps(List<String> knownApps) { this.knownApps = knownApps; }
}
