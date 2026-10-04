# Raft kv
- just building a consistent & partition tolerant distributed Kv store
- this cud be used as plugin for databases, instances and many more :)
## Layer1: raft core engine
- so basically this module consists of core algorithm implementation of raft
- no network stuff included here, pure focus on core raft algo
- it mainly manages state of node, caching and persisting info
- provides apis for grpc server to manage node(itself)
- so wat i did here
  - defined messages and services in [raft.proto](./app/src/main/proto/raft.proto)
  - added protobuf plugins in [build.gradle](./app/build.gradle.kts)
  - ``./gradlew generateProto`` builds all the stub classes for us to use (message stub in [main](./app/build/generated/source/proto/main/java) and services stub in [grpc](./app/build/generated/source/proto/main/grpc)) 
  - defined [NodeState](./app/src/main/java/org/example/core/enums/NodeState.java) and [LogEntry](./app/src/main/java/org/example/core/enums/LogEntry.java) which are both base state models
  - implemented disk and cache save of Log entries in [PersistentState](app/src/main/java/org/example/core/logManagement/persitence/PersistentState.java), tis class provides us thread safe APIs using synchronized for serving all operations related to Log file management, like appending entries in log file,updating metadata, truncating log file and loading cache values and serving tem upon restart
  - but i dint want the server to directy interact with PersistentState class, so i built another [RaftLog](app/src/main/java/org/example/core/logManagement/RaftLog.java) class, this will be used by server to manage logs instead of persistent state class directly, also it provides multiple additional low level APIs on log entries
## Layer2: timers and network interfaces
- so basically till now we just have built state parameters that each node will have, ie metadata and node log entries
- but this node also needs to conduct an election upon election timeout and listen to heartbeats, so now we need to make it run a timer internally for election purposes
- implemented [ElectionTimer](app/src/main/java/org/example/core/timer/ElectionTimer.java), it provides reset and stop APIs, reset wud start callback process provided during object creation in background at randomized time intervals
- defined all callbacks in [RaftNodeListener.java](app/src/main/java/org/example/core/listeners/RaftNodeListener.java) interface, that wud be implemented by node itself and wud hv logic of what callbacks must do