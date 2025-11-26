package com.example.artemis_leak_repro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ArtemisMonitor {

    private final EmbeddedActiveMQ embeddedActiveMQ;

    public Map<String, Integer> getCacheSizes() {
        Map<String, Integer> map = new HashMap<>();
        PagingManagerImpl pagingManager = (PagingManagerImpl) embeddedActiveMQ.getActiveMQServer().getPagingManager();
        PostOfficeImpl postOffice = (PostOfficeImpl) embeddedActiveMQ.getActiveMQServer().getPostOffice();
        Set<SimpleString> addresses = postOffice.getAddresses();
        int queueCount = postOffice.getAllBindings().map(b -> ((Queue) b.getBindable()).getName()).collect(Collectors.toSet()).size();
        int publishQueueCount = postOffice.getAllBindings().filter(b -> ((Queue) b.getBindable()).getName().toString().startsWith("publish/")).collect(Collectors.toSet()).size();
        ConcurrentMap<SimpleString, DuplicateIDCache> duplicateIDCaches = postOffice.getDuplicateIDCaches();
        map.put("dup-cache-size", duplicateIDCaches.size());
        map.put("dup-cache-bridges", duplicateIDCaches
            .keySet().stream()
            .filter(ss -> ss.toString().startsWith("BRIDGE"))
            .collect(Collectors.toSet())
            .size());
        map.put("pm-stores", pagingManager.getStoreNames().length);
        map.put("pm-stores-publish-addrs", Arrays.stream(pagingManager.getStoreNames())
            .filter(ss -> ss.toString().startsWith("publish/"))
            .collect(Collectors.toSet()).size());
        map.put("publish-addresses", addresses.stream().filter(ss -> ss.toString().startsWith("publish/"))
            .collect(Collectors.toSet())
            .size());
        map.put("address-count", addresses.size());
        map.put("queue-count", queueCount);
        map.put("publish-queue-count", publishQueueCount);
        return map;
    }

    public int getClusterNodeCount() {
        ClusterManager clusterManager = embeddedActiveMQ.getActiveMQServer().getClusterManager();
        if (clusterManager != null && clusterManager.getDefaultConnection(null) != null) {
            Topology topology = clusterManager.getDefaultConnection(null).getTopology();
            if (topology != null) {
                return topology.getMembers().size();
            }
        }
        return 1;
    }

}
