# NOSQL VLOCK PROJECT-AP MODE WITH CONSISTENT HASHING 

To begin with, the node cluster works is working in AP node with all nodes in a partition synced to each other. Thus, all data is replicated across all nodes.

## My Implementation
In my implementation, the keyhash function is used to map to a particular node in the cluster called target node. Target node +1 and +2 serve as replicas on which the key is also stored. Create, update and delete actions are only handled by the primary node while read action can be performed at any of these.

All the other nodes will attempt to forward any CRUD requests to the target node first based on hash value of the key. If target node is unavailable, read requests are forwarded to first available replica. If target node is unavailable, create, update, and delete requests generate an error.





