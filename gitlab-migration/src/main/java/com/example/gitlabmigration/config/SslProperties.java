package com.example.gitlabmigration.config;

/**
 * SSL truststore configuration for connecting to GitLab HTTPS endpoints.
 * When truststorePath is blank, the JVM default trust is used.
 */
public class SslProperties {

    private String truststorePath = "";
    private String truststorePassword = "changeit";
    private String truststoreType = "JKS"; // JKS or PKCS12
    private boolean skipHostnameVerification = false;

    public String getTruststorePath() { return truststorePath; }
    public void setTruststorePath(String truststorePath) { this.truststorePath = truststorePath; }

    public String getTruststorePassword() { return truststorePassword; }
    public void setTruststorePassword(String truststorePassword) { this.truststorePassword = truststorePassword; }

    public String getTruststoreType() { return truststoreType; }
    public void setTruststoreType(String truststoreType) { this.truststoreType = truststoreType; }

    public boolean isSkipHostnameVerification() { return skipHostnameVerification; }
    public void setSkipHostnameVerification(boolean skipHostnameVerification) {
        this.skipHostnameVerification = skipHostnameVerification;
    }
}
