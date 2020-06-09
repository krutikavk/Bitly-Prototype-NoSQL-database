# NoSQL vClock Project

This project is a simulation of vlocks implementation in an AP database. 


## High Level Steps
I followed the following steps for adding required conflict detection and resolution to the existing startup code.

1. Added functionality to update and delete document information to Sync calls on ports 8xxx.
2. Enhanced the syncing between nodes to dequeue from the sync queue only if a node is available. The updates for an unreachable node remain queued until the partition is resolved.
3. Conflict Detection and Resolution implemented for the following scenarios:
  * API calls for PUT and DELETE calls on AdminServer
  * Sync on CREATE, UPDATE, DELETE calls on AppServer
  * Tombstones functionality added to track deleted document on each node


## Conflict Detection and Resolution
Functions isConflict() and mergeVClocks() in API.java are used to detect conflict condition between 2 nodes and resolve it. </br> 
isConflict() detects a conflict if any incoming vclock tag is less than local vclock tag. </br>
```
local vclock : [630ace654144, 630ace654144:0, 61b6727dd110:3, null, null, null]
incoming vclock from node 3 : [35ba41e901a8, null, 61b6727dd110:1, 35ba41e901a8:0, null, null]

Conflict Condition: incoming(node2:vlock tag) < local(node3:vclock tag)
```


Function mergeVClocks() has a dual intent:
1. Merge vclocks from incoming and local vclocks 
   For example at Node1:
   ```
   local vclock : [630ace654144, 630ace654144:0, 61b6727dd110:1, null, null, null]
   incoming vclock from node 3 : [35ba41e901a8, null, 61b6727dd110:1, 35ba41e901a8:1, null, null]
   
   Merged vclock at node 1: [630ace654144, 630ace654144:0, 61b6727dd110:1, 35ba41e901a8:1, null, null]
   ```
   
2. Declare winner of a conflict in case there is one. The return value of mergeVClocks is used in case a conflict is detected. Highest node ID having a vclock change from local to incoming is the conflict winner.
   For example at Node1:
   ```
   local vclock : [630ace654144, 630ace654144:0, 61b6727dd110:3, null, null, null]
   incoming vclock from node 3 : [35ba41e901a8, null, 61b6727dd110:1, 35ba41e901a8:0, null, null]

   Conflict Condition: incoming(node2:vlock tag) < local(node3:vclock tag)
   Conflict Winner: Node 3
   ```
  
## Tombstones
Documents are never deleted from this system once added to a node. Any available document on a node will have a tombstone = false. A Delete action on the document will simply mark the same with a tombstone = true. This, for all intents and purposes from point of view of a user, is a deleted document and will behave as if there were no requested document on the system.

Since deleted documents will still exist in the system, subsequent POST on API 9xxx on the same document will increment the vclock as well as reset tombstone value. API and Sync calls are also appropriately modified to handle the presence of a tombstone.

## References

[https://riak.com/why-vector-clocks-are-easy/index.html] </br>
The classic Alice, Ben, Cathy, and Dave's example was a good starting point to begin planning the implementation.

[https://docs.riak.com/riak/kv/latest/developing/usage/conflict-resolution/index.html] </br>
This resource was a stepping stone to capturing requiments of conflict detection and subsequent resolution by identifying successors/descendents of messages.

[https://www.waitingforcode.com/big-data-algorithms/conflict-resolution-distributed-applications-vector-clocks/read] </br>
This example helped me understand the working of vclocks and build up a lot of pseudo-scenarios to test my implementation.

[https://docs.riak.com/riak/kv/latest/using/reference/object-deletion/index.html] </br>
The requirement for deletion of documents became much clearer from a close inspection of failure cases in tests 4, 5, 6, and 8. This resource helped point to the correct direction for implementation of tombstones for documents supplemented with a lot of whiteboard examples and scenarios.



