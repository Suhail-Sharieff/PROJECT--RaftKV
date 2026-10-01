# Raft kv
- just building a consistent & partition tolerant distributed Kv store
- this cud be used as plugin for databases, instances and many more :)
## Phase1: impl raft core
- so basically this module consists of core algorithm implementation of raft
- no network stuff included here, pure focus on core raft algo
- it mainly manages state of node, caching and persisting info
- provides apis for grpc server to manage node(itself)