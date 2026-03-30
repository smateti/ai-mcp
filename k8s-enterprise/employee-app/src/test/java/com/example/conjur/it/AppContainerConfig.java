package com.example.conjur.it;

import org.microshed.testing.SharedContainerConfiguration;
import org.microshed.testing.testcontainers.ApplicationContainer;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.junit.jupiter.Container;

public class AppContainerConfig implements SharedContainerConfiguration {

    @Container
    public static Db2Container db2 = new Db2Container("icr.io/db2_community/db2:11.5.8.0")
            .acceptLicense()
            .withNetworkAliases("testdb")
            .withDatabaseName("empdb")
            .withUsername("db2inst1")
            .withPassword("appTestPass123")
            .withInitScript("init-test.sql");

    @Container
    public static ApplicationContainer app = new ApplicationContainer()
            .withAppContextRoot("/api")
            .withReadinessPath("/health/ready")
            .withEnv("DB_HOST", "testdb")
            .withEnv("DB_PORT", "50000")
            .withEnv("DB_NAME", "empdb")
            .withEnv("DB_USERNAME", "db2inst1")
            .withEnv("DB_PASSWORD", "appTestPass123")
            .withEnv("DB_CONJUR_UID_KEY", "db/empdb-uid")
            .withEnv("DB_CONJUR_PWD_KEY", "db/empdb-pwd")
            // Skip Conjur in test mode
            .withEnv("CONJUR_AUTHN_API_KEY", "not-set")
            .dependsOn(db2);
}
