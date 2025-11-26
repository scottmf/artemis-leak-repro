package com.example.artemis_leak_repro;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({AmqProperties.class, ReproProperties.class})
public class ArtemisLeakReproApplication implements ApplicationListener<ApplicationReadyEvent> {

	public static void main(String[] args) {
//        cleanupArtemisDataDirectory("target/artemis-data-node1/");
//        cleanupArtemisDataDirectory("target/artemis-data-node2/");
		SpringApplication.run(ArtemisLeakReproApplication.class, "--spring.profiles.active=node1");
        SpringApplication.run(ArtemisLeakReproApplication.class, "--spring.profiles.active=node2");
	}

    @Bean
    CoreProtocolPublisher coreProtocolPublisher(
        EmbeddedActiveMQ embeddedActiveMQ, Diagnostics diags, AmqProperties amqProps, ReproProperties reproProps
    ) {
        return new CoreProtocolPublisher(embeddedActiveMQ, diags, amqProps, reproProps);
    }

    @Bean
    @ConditionalOnBooleanProperty("repro.core-consumer-enabled")
    public CoreProtocolConsumer coreProtocolConsumer(AmqProperties amqProps, ReproProperties reproProps) {
        return new CoreProtocolConsumer(amqProps, reproProps);
    }

    @Bean
    @ConditionalOnBooleanProperty(value = "repro.core-consumer-enabled", havingValue = false)
    public MqttConsumer mqttConsumer(EmbeddedActiveMQ embeddedActiveMQ, AmqProperties amqProps, ReproProperties reproProps) {
        return new MqttConsumer(embeddedActiveMQ, amqProps, reproProps);
    }

    @Bean
    Diagnostics diagnosticThread(ArtemisMonitor monitor, AmqProperties amqProperties) {
        return new Diagnostics(monitor, amqProperties);
    }

    @Bean
    ArtemisMonitor artemisMonitor(EmbeddedActiveMQ embeddedAmq) {
        return new ArtemisMonitor(embeddedAmq);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        CoreProtocolPublisher publisher = event.getApplicationContext().getBean(CoreProtocolPublisher.class);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "publisher");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) -> {
                log.error("Uncaught exception in thread {}", thread.getName(), ex);
            });
            return t;
        });

        log.info("Starting scheduled publisher");
        scheduler.scheduleWithFixedDelay(publisher::publishToMultipleAddresses, 5, 10, TimeUnit.SECONDS);
    }

    @SneakyThrows
    private static void cleanupArtemisDataDirectory(String path) {
        Path artemisDataPath = Path.of(path);
        if (Files.exists(artemisDataPath)) {
            log.info("Cleaning up existing artemis-data directory: {}", artemisDataPath.toAbsolutePath());
            try (Stream<Path> walk = Files.walk(artemisDataPath)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            log.info("Artemis data directory cleaned");
        }
    }
}
