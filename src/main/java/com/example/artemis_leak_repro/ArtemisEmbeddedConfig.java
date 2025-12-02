package com.example.artemis_leak_repro;

import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@org.springframework.context.annotation.Configuration
public class ArtemisEmbeddedConfig {

    @Bean
    ArtemisConfigurationCustomizer reproCustomizer(AmqProperties amqProperties) {
        return config -> {
            config.setName(amqProperties.getBrokerName());

            config.setSecurityEnabled(false);
            config.setPersistenceEnabled(true); // Enable to create paging stores per address
            String dataDir = amqProperties.getDataDirectory();
            config.setJournalDirectory(dataDir + "/journal");
            config.setBindingsDirectory(dataDir + "/bindings");
            config.setPagingDirectory(dataDir + "/paging");
            config.setLargeMessagesDirectory(dataDir + "/largemessages");

            config.setJournalType(JournalType.NIO);
            config.setJournalFileSize(1024 * 1024);
            config.setGlobalMaxSize(10 * 1024 * 1024);
            config.setJournalMinFiles(2);
            config.setJournalCompactMinFiles(0);
            config.setJournalCompactPercentage(0);
            // not part of our prod configuration, only for repro
            config.setJournalSyncNonTransactional(false);
            config.setJournalSyncTransactional(false);

            config.setJMXManagementEnabled(true);
            config.setAddressQueueScanPeriod(500);

            config.setPersistIDCache(true);

            Set<TransportConfiguration> acceptors = new HashSet<>();

            Map<String, Object> coreParams = new HashMap<>();
            coreParams.put("protocols", "CORE");
            coreParams.put("host", amqProperties.getHost());
            coreParams.put("port", String.valueOf(amqProperties.getCorePort()));
            acceptors.add(new TransportConfiguration(NettyAcceptorFactory.class.getName(), coreParams, "core"));

            Map<String, Object> mqttParams = new HashMap<>();
            mqttParams.put("protocols", "MQTT");
            mqttParams.put("host", amqProperties.getHost());
            mqttParams.put("port", String.valueOf(amqProperties.getMqttPort()));
            acceptors.add(new TransportConfiguration(NettyAcceptorFactory.class.getName(), mqttParams, "mqtt"));

            config.setAcceptorConfigurations(acceptors);

            Map<String, Object> connectorParams = new HashMap<>();
            connectorParams.put("host", amqProperties.getHost());
            connectorParams.put("port", String.valueOf(amqProperties.getCorePort()));
            config.addConnectorConfiguration("netty-connector",
                new TransportConfiguration(NettyConnectorFactory.class.getName(),
                    connectorParams));

            ClusterConnectionConfiguration clusterConfig = new ClusterConnectionConfiguration()
                .setName(amqProperties.getClusterConnectionName())
                .setClientId("cluster-" + amqProperties.getBrokerName())
                .setConnectorName("netty-connector")
                .setClusterNotificationInterval(1000)
                .setClusterNotificationAttempts(120)
                .setClientFailureCheckPeriod(30000)
                .setConnectionTTL(60000)
                .setRetryInterval(2000)
                .setMaxRetryInterval(20000)
                .setRetryIntervalMultiplier(2.0)
                .setReconnectAttempts(-1)
                .setInitialConnectAttempts(-1)
                .setDuplicateDetection(true)
                .setAllowDirectConnectionsOnly(false)
                .setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND)
                .setMaxHops(1)
                .setProducerWindowSize(-1);

            List<String> staticConnectors = new ArrayList<>();
            staticConnectors.add("netty-connector");

            String brokerName = amqProperties.getBrokerName();

            if (brokerName.equals("artemis-broker")) {
                setConnector(config, staticConnectors, "61617", "node1-connector");
                setConnector(config, staticConnectors, "61618", "node2-connector");
            } else if (brokerName.equals("artemis-node1")) {
//                setConnector(config, staticConnectors, "61616", "default-connector");
                setConnector(config, staticConnectors, "61618", "node2-connector");
            } else if (brokerName.equals("artemis-node2")) {
//                setConnector(config, staticConnectors, "61616", "default-connector");
                setConnector(config, staticConnectors, "61617", "node1-connector");
            }

            clusterConfig.setStaticConnectors(staticConnectors);

            config.addClusterConfiguration(clusterConfig);

            log.info("Cluster configuration enabled: {} (static connectors)",
                amqProperties.getClusterConnectionName());
            log.info("  Static connectors: {}", staticConnectors);

            WildcardConfiguration wildcardConfig = new WildcardConfiguration();
            wildcardConfig.setDelimiter('/');
            wildcardConfig.setAnyWords('#');
            wildcardConfig.setSingleWord('+');
            config.setWildCardConfiguration(wildcardConfig);

            log.info("Wildcard configuration set: delimiter='/', anyWords='#', singleWord='+'");

            // Address settings with auto-delete enabled
            AddressSettings addressSettings = new AddressSettings();
            addressSettings.setAutoCreateAddresses(true);
            addressSettings.setAutoDeleteAddresses(true);
            // Delete immediately
            addressSettings.setAutoDeleteAddressesDelay(0L);
            addressSettings.setAutoCreateQueues(true);
            addressSettings.setAutoDeleteQueues(true);
            addressSettings.setAutoDeleteQueuesDelay(0L);
            addressSettings.setAutoDeleteQueuesMessageCount(0L);

            addressSettings.setMaxSizeBytes(10 * 1024 * 1024L);
            addressSettings.setPageSizeBytes(1024 * 1024);
            addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);

            addressSettings.setIDCacheSize(2000);

            config.addAddressSetting("#", addressSettings);
        };
    }

    private void setConnector(Configuration config, List<String> staticConnectors, String port, String connectorName) {
        Map<String, Object> nodeConnector = new HashMap<>();
        nodeConnector.put("host", "localhost");
        nodeConnector.put("port", port);
        config.addConnectorConfiguration(connectorName,
            new TransportConfiguration(NettyConnectorFactory.class.getName(), nodeConnector));
        staticConnectors.add(connectorName);
    }

}
