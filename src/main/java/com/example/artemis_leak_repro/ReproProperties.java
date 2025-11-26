package com.example.artemis_leak_repro;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for memory leak reproduction scenario.
 * Maps to {@code repro.*} properties in application.yaml
 */
@Data
@ConfigurationProperties(prefix = "repro")
public class ReproProperties {

    /**
     * Number of unique publish addresses to create.
     * Each message is published to a unique address like {@code publish/{orgId}/{userId}}.
     * Higher values increase memory pressure and demonstrate the leak more clearly.
     * Default: 100
     */
    private int addressCount = 1000;

    /**
     * Enable Core protocol consumers for comparison.
     * When true, creates Core protocol consumers that subscribe to {@code publish/#}.
     * Useful for comparing MQTT vs Core protocol auto-delete behavior.
     * Default: false
     */
    private boolean coreConsumerEnabled = false;

}

