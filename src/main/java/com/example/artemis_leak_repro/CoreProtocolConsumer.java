package com.example.artemis_leak_repro;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumes messages using Core protocol with wildcard subscriptions.
 * This is a control to show whether Core protocol has the same auto-delete issue as MQTT.
 */
@Slf4j
@RequiredArgsConstructor
public class CoreProtocolConsumer {

    private final AmqProperties amqProperties;
    private final ReproProperties reproProperties;

    private ClientSessionFactory sessionFactory;
    private final AtomicLong messageCount = new AtomicLong();
    private ClientSession session;
    private ClientConsumer consumer;
    private ServerLocator locator;

    @PostConstruct
    public void init() throws Exception {
        String brokerUrl = String.format("tcp://%s:%d", amqProperties.getHost(), amqProperties.getCorePort());
        this.locator = ActiveMQClient.createServerLocator(brokerUrl);
        this.sessionFactory = locator.createSessionFactory();
        createConsumer();
    }

    @PreDestroy
    @SneakyThrows
    public void destroy() {
        consumer.close();
        session.close();
        sessionFactory.close();
        locator.close();
    }

    private void createConsumer() throws Exception {
        this.session = sessionFactory.createSession();
        this.session.start();

        String queueName = "core-consumer-" + UUID.randomUUID();
        String wildcardAddress = "publish/#";

        QueueConfiguration queueConfig = QueueConfiguration.of(queueName)
            .setAddress(wildcardAddress)
            .setRoutingType(RoutingType.MULTICAST)
            .setDurable(false)
            .setAutoCreated(true)
            .setTemporary(true);

        session.createQueue(queueConfig);

        this.consumer = session.createConsumer(queueName);
        consumer.setMessageHandler(message -> {
            try {
                long count = messageCount.incrementAndGet();
                if ((count % reproProperties.getAddressCount()) == 0) {
                    log.info("[Core Consumer] Received {} messages", count);
                }
                message.acknowledge();
            } catch (ActiveMQException e) {
                log.error("Error processing message in Core consumer", e);
            }
        });
    }

}
