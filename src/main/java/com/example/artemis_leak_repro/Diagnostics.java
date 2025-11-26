package com.example.artemis_leak_repro;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Background thread that periodically logs diagnostic information about the broker state.
 */
@Slf4j
@RequiredArgsConstructor
public class Diagnostics {

    private final ArtemisMonitor monitor;
    private final AmqProperties amqProperties;

    void logDiagnostics() {
        try {
            Map<String, Integer> stats = monitor.getCacheSizes();
            String dir = amqProperties.getDataDirectory();
            StringBuilder b = new StringBuilder();
            b.append("\n=========================\n");
            b.append(String.format("===== %s =====\n", amqProperties.getBrokerName()));
            b.append("=========================\n");
            b.append(String.format("Cluster Nodes:    %5s\n", monitor.getClusterNodeCount()));
            b.append(String.format("Addresses:        %5s total, %5s publish/* addresses\n", stats.get("address-count"), stats.get("publish-addresses")));
            b.append(String.format("Queues:           %5s total, %5s publish/# wildcard queues\n", stats.get("queue-count"), stats.get("publish-queue-count")));
            b.append(String.format("DuplicateIDCache: %5s total, %5s BRIDGE caches\n", stats.get("dup-cache-size"), stats.get("dup-cache-bridges")));
            b.append(String.format("PagingStores:     %5s total, %5s publish/* stores\n", stats.get("pm-stores"), stats.get("pm-stores-publish-addrs")));
            b.append(String.format("Journal Size:     %5s MB (%s)\n", getDirectorySize(dir), dir));
            b.append("==========================");
            log.info(b.toString());
        } catch (Exception e) {
            log.error("Error logging diagnostics", e);
        }
    }

    @SneakyThrows
    public String getDirectorySize(String path) {
        Path folder = Paths.get(path);

        try (Stream<Path> walk = Files.walk(folder)) {
            return String.valueOf(walk
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum() / 1024 / 1024);
        }
    }

}
