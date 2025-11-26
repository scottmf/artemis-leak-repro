package com.example.artemis_leak_repro;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for ActiveMQ Artemis broker settings.
 * Maps to {@code amq.*} properties in application.yaml
 */
@Data
@ConfigurationProperties(prefix = "amq")
public class AmqProperties {

    /**
     * Unique name for this broker instance.
     * Used in JMX monitoring and cluster identification.
     * Default: "artemis-broker"
     */
    private String brokerName = "broker";

    /**
     * Port for MQTT protocol connections.
     * Used by MqttConsumer to connect to the broker.
     * Default: 1883 (standard MQTT port)
     */
    private int mqttPort = 1883;

    /**
     * Port for Artemis Core protocol connections.
     * Used by CoreProtocolPublisher and CoreProtocolConsumer.
     * Default: 61616 (standard Artemis Core port)
     */
    private int corePort = 61616;

    /**
     * Host address for broker acceptors and client connections.
     * Default: "localhost"
     */
    private String host = "localhost";

    /**
     * Directory path for broker persistence.
     * Stores journal, bindings, paging stores, and large messages.
     * Default: "target/artemis-data"
     */
    private String dataDirectory = "target/artemis-data";

    /**
     * Name for the cluster connection configuration.
     * Used when cluster-enabled is true.
     * Default: "artemis-cluster"
     */
    private String clusterConnectionName = "artemis-cluster";

    /**
     * Enable cluster mode with static connectors.
     * When true, configures full mesh topology between default, node1, and node2.
     * Default: true
     */
    private boolean clusterEnabled = true;
}

