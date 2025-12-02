package com.example.artemis_leak_repro;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates MQTT consumers that subscribe to publish/# with shared subscriptions.
 * This simulates EBS consuming events via wildcard subscription.
 * Uses HiveMQ MQTT Client (modern, bug-free alternative to Paho).
 */
@Slf4j
@RequiredArgsConstructor
public class MqttConsumer {

    private final List<Mqtt5BlockingClient> consumers = new ArrayList<>();
    private final AtomicInteger messageCount = new AtomicInteger(0);

    private final EmbeddedActiveMQ embeddedActiveMQ;
    private final AmqProperties amqProperties;
    private final ReproProperties reproProperties;

    @PostConstruct
    public void init() throws Exception {
        // Wait for broker to be ready
        waitForBroker();

        log.info("Starting MQTT consumers");

        createConsumer();

        log.info("MQTT consumer started successfully");
    }

    private void createConsumer() throws Exception {
        String clientId = "consumer-" + UUID.randomUUID();

        // Create HiveMQ MQTT 5 async client for callback support
        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(amqProperties.getHost())
                .serverPort(amqProperties.getMqttPort())
                .buildAsync();

        // Connect with clean start (temporary session)
        log.info("Consumer connecting to broker...");
        client.connectWith()
                .cleanStart(true)
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        log.error("Consumer failed to connect: {}", throwable.getMessage());
                    } else {
                        log.info("Consumer connected successfully");
                    }
                })
                .join(); // Wait for connection

        // Subscribe to wildcard with or without shared subscription
        String subscriptionTopic = "$share/ebs-group/publish/#";

        // Subscribe and set up message callback
        // QoS 1 (AT_LEAST_ONCE) matches production EBS configuration
        log.info("Consumer subscribing to: {}", subscriptionTopic);

        client.subscribeWith()
                .topicFilter(subscriptionTopic)
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE) // QoS 1
                .callback(publish -> {
                    int count = messageCount.incrementAndGet();
                    if ((count % reproProperties.getAddressCount()) == 0) {
                        log.info("[MQTT Consumer] Received {} messages", count);
                    }
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        log.error("Consumer subscription FAILED: {}", throwable.getMessage());
                    } else {
                        log.info("Consumer subscription CONFIRMED for: {}", subscriptionTopic);
                    }
                })
                .join(); // Wait for subscription

        // Store as blocking client for cleanup
        consumers.add(client.toBlocking());
        log.info("Consumer setup complete");
    }

    private void waitForBroker() throws InterruptedException {
        // Wait for broker to be fully started
        int retries = 30;
        while (retries > 0 && !embeddedActiveMQ.getActiveMQServer().isStarted()) {
            log.info("Waiting for broker to start...");
            Thread.sleep(100);
            retries--;
        }
        if (!embeddedActiveMQ.getActiveMQServer().isStarted()) {
            throw new IllegalStateException("Broker did not start in time");
        }
        // Additional delay to ensure MQTT acceptor is ready
        Thread.sleep(500);
    }

    @PreDestroy
    public void cleanup() throws Exception {
        log.info("Disconnecting {} MQTT consumers...", consumers.size());
        for (int i = 0; i < consumers.size(); i++) {
            Mqtt5BlockingClient client = consumers.get(i);
            if (client != null && client.getState().isConnected()) {
                client.disconnect();
                log.info("Consumer {} disconnected", i);
            }
        }
        log.info("All MQTT consumers disconnected");
    }
}
