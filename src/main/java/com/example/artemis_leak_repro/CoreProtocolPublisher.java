package com.example.artemis_leak_repro;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import java.util.UUID;

/**
 * Publishes messages using Artemis Core protocol (non-MQTT) to demonstrate
 * that auto-delete works correctly when not using MQTT wildcard subscriptions.
 * This is the CONTROL test showing expected behavior.
 */
@Slf4j
@RequiredArgsConstructor
public class CoreProtocolPublisher {

    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000001";

    private final EmbeddedActiveMQ embeddedActiveMQ;
    private final Diagnostics diagnostics;
    private final AmqProperties amqProperties;
    private final ReproProperties reproProperties;

    private ServerLocator locator;
    private ClientSessionFactory sessionFactory;
    private ClientSession session;

    @PostConstruct
    public void init() throws Exception {
        // Wait for broker to be ready
        String brokerUrl = String.format("tcp://%s:%d", amqProperties.getHost(), amqProperties.getCorePort());
        this.locator = ActiveMQClient.createServerLocator(brokerUrl);
        this.sessionFactory = locator.createSessionFactory();
        this.session = sessionFactory.createSession();
        session.start();
        log.info("Core Protocol Publisher initialized");
    }

    /**
     * Publishes messages to multiple unique addresses using Core protocol.
     * Creates a fresh session for each call to avoid "session closed" errors.
     */
    @SneakyThrows
    public void publishToMultipleAddresses() {
        diagnostics.logDiagnostics();

        int addressCount = reproProperties.getAddressCount();
        for (int i = 0; i < addressCount; i++) {
            String orgId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            String address = i == 0 ?
                String.format("publish/%s/%s", ZERO_UUID, ZERO_UUID) :
                String.format("publish/%s/%s", orgId, userId);

            // Create message
            ClientMessage message = session.createMessage(true);
            String payload = String.format("{\"eventType\": \"test.event\", \"index\": %d, \"timestamp\": %d}",
                i, System.currentTimeMillis());
            message.getBodyBuffer().writeString(payload);
            message.setRoutingType(RoutingType.MULTICAST);
            try (ClientProducer producer = session.createProducer(address)) {
                producer.send(message);
            }
        }

        log.info("[Core Publisher] Finished publishing to {} addresses", addressCount);
    }

    @PreDestroy
    @SneakyThrows
    public void cleanup() {
        session.close();
        sessionFactory.close();
        locator.close();
        log.info("Core Protocol Publisher shutdown");
    }
}

