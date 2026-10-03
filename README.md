# Raft kv
- just building a consistent & partition tolerant distributed Kv store
- this cud be used as plugin for databases, instances and many more :)
## Phase1: impl raft core
- so basically this module consists of core algorithm implementation of raft
- no network stuff included here, pure focus on core raft algo
- it mainly manages state of node, caching and persisting info
- provides apis for grpc server to manage node(itself)
- so wat i did here
  - defined messages and services in [raft.proto](./app/src/main/proto/raft.proto)
  - added protobuf plugins in [build.gradle](./app/build.gradle.kts)
  - ``./gradlew generateProto`` builds all the stub classes for us to use (message stub in [main](./app/build/generated/source/proto/main/java) and services stub in [grpc](./app/build/generated/source/proto/main/grpc)) 
  - defined [NodeState](./app/src/main/java/org/example/core/enums/NodeState.java) and [LogEntry](./app/src/main/java/org/example/core/records/LogEntry.java) which are both base state models
