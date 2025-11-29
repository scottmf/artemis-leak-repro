# ActiveMQ Artemis ARTEMIS-5773 Repro

## To Reproduce

```sh
# if you don't have `timeout` installed
brew install coreutils

# reproduces the issue
timeout 90s ./gradlew bootRun; while [ $(jps | grep ArtemisLeakReproApplication | awk '{print $1}') ]; do sleep 1; done; timeout 90s ./gradlew bootRun

# proves #6085 cleans up the affected caches
timeout 1m ./gradlew bootRun -PartemisVersion=2.44.0-6085

# to clear journal
rm -r target/*
```

## Information on repro

* Launches a two node cluster
* Publishes 1000 messages in batches (on both nodes)
  * Publisher will always publish to different addresses in the form `publish/{uuid}/{uuid}`
* Consumes 1000 messages in batches (on both nodes)
  * Listens on wildcard address `publish/#`

## Problem

1. Addresses are never auto-deleted
2. Between runs the BRIDGE caches are never cleaned up

This causes a leak in `PostOfficeImpl.duplicateIDCache`, `PagingManagerImpl.stores` and to some degree `AddressManager.addressMap`

### Issues at play

1. All addresses created are non-durable / temporary / auto-delete=true but never cleaned up.  I believe this is due to the wildcard binding on `publish/#`.
   IMO this has to be a bug since these addresses are not part of the non-volatile state and are not reflected once the nodes are restarted.
   State between runtimes should match unless configuration is changed.
2. BRIDGE caches are not cleaned up.  This fix is reflected in [#6085](https://github.com/apache/activemq-artemis/pull/6085)

### Run 1

#### Last diagnostic of the first run
```
=========================
===== artemis-node2 =====
=========================
Cluster Nodes:        2
Addresses:         2042 total,  2037 publish/* addresses       <<<< ADDRESSES are never purged, though they are non-durable / temporary / auto-delete=true
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  1038 total,  1038 BRIDGE caches
PagingStores:      2044 total,  2038 publish/* stores
Journal Size:         5 MB (target/artemis-data-node2)
==========================

=========================
===== artemis-node1 =====
=========================
Cluster Nodes:        2
Addresses:         4003 total,  3998 publish/* addresses       <<<< ADDRESSES are never purged, though they are non-durable / temporary / auto-delete=true
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  1999 total,  1999 BRIDGE caches
PagingStores:      4004 total,  3998 publish/* stores
Journal Size:         6 MB (target/artemis-data-node1)
==========================
```

### Run 2
This is most noticeable immediately after startup, the `duplicateIDCache` is populated from the previous run and addresses do not match

#### First log of second run:
```
=========================
===== artemis-node1 =====
=========================
Cluster Nodes:        2
Addresses:            6 total,     1 publish/* addresses       <<<< ADDRESSES have been purged
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  1999 total,  1999 BRIDGE caches             <<<< BRIDGE caches populated from the journal for temporary / non-durable / auto-delete=true addresses
PagingStores:         7 total,     1 publish/* stores
Journal Size:         6 MB (target/artemis-data-node1)
==========================

=========================
===== artemis-node2 =====
=========================
Cluster Nodes:        2
Addresses:            6 total,     1 publish/* addresses       <<<< ADDRESSES have been purged
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  2069 total,  2069 BRIDGE caches             <<<< BRIDGE caches populated from the journal for temporary / non-durable / auto-delete=true addresses
PagingStores:         7 total,     1 publish/* stores
Journal Size:         6 MB (target/artemis-data-node2)
==========================
```

#### Final diagnostic of second run

```
=========================
===== artemis-node1 =====
=========================
Cluster Nodes:        2
Addresses:         2005 total,  2000 publish/* addresses
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  2998 total,  2998 BRIDGE caches             <<<< BRIDGE caches continue to accumulate
PagingStores:      2006 total,  2000 publish/* stores
Journal Size:         8 MB (target/artemis-data-node1)
==========================

=========================
===== artemis-node2 =====
=========================
Cluster Nodes:        2
Addresses:         3004 total,  2999 publish/* addresses
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:  4067 total,  4067 BRIDGE caches             <<<< BRIDGE caches continue to accumulate
PagingStores:      3005 total,  2999 publish/* stores
Journal Size:         8 MB (target/artemis-data-node2)
==========================
```

### Run 3 with fix from #6085

#### Initial diagnostic before publishing begins
```
=========================
===== artemis-node1 =====
=========================
Cluster Nodes:        2
Addresses:            8 total,     3 publish/* addresses
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:     2 total,     2 BRIDGE caches             <<<< BRIDGE caches have been cleared
PagingStores:         6 total,     1 publish/* stores
Journal Size:         9 MB (target/artemis-data-node1)
==========================

=========================
===== artemis-node2 =====
=========================
Cluster Nodes:        2
Addresses:            6 total,     1 publish/* addresses
Queues:               6 total,     0 publish/# wildcard queues
DuplicateIDCache:     0 total,     0 BRIDGE caches             <<<< BRIDGE caches have been cleared
PagingStores:         6 total,     1 publish/* stores
Journal Size:         8 MB (target/artemis-data-node2)
==========================
```

