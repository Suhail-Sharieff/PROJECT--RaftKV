# raft-kv-java

A distributed, fault-tolerant key-value store built in Java 21, implementing the **Raft consensus algorithm** for leader election and log replication. It uses gRPC for high-performance inter-node communication and client operations, with durable state persistence and structured logging.


This project is structured as a multi-module Gradle project, strictly separating the core consensus algorithm from network and transport concerns. This architecture makes the Raft state machine 100% testable without any mock sockets or RPC channels.

---

## Architecture & Design Decisions

### System Component Diagram
The system separates transport, consensus, and database concerns. Below is the detailed architecture of a 3-node cluster, showing the internal design of each node and client-leader routing:

```mermaid
flowchart TB
    subgraph ClientSpace [Client Application]
        Client[RaftCliClient]
    end

    subgraph Node1 [Raft Node 1 - Leader]
        direction TB
        Server1[gRPC Server]
        Core1[RaftNode Engine]
        Log1[(RaftLog / Disk WAL)]
        DB1[(KV Database Map)]
        
        Server1 <-->|Translate Msg| Core1
        Core1 <-->|Read / Write| Log1
        Core1 -->|onCommit| DB1
    end

    subgraph Node2 [Raft Node 2 - Follower]
        direction TB
        Server2[gRPC Server]
        Core2[RaftNode Engine]
        Log2[(RaftLog / Disk WAL)]
        DB2[(KV Database Map)]
        
        Server2 <-->|Translate Msg| Core2
        Core2 <-->|Read / Write| Log2
        Core2 -->|onCommit| DB2
    end

    subgraph Node3 [Raft Node 3 - Follower]
        direction TB
        Server3[gRPC Server]
        Core3[RaftNode Engine]
        Log3[(RaftLog / Disk WAL)]
        DB3[(KV Database Map)]
        
        Server3 <-->|Translate Msg| Core3
        Core3 <-->|Read / Write| Log3
        Core3 -->|onCommit| DB3
    end

    Client -->|1. PUT / GET| Server1
    Client -.->|Redirect / Failover| Server2
    
    Core1 -->|2. AppendEntries RPC| Server2
    Core1 -->|2. AppendEntries RPC| Server3
```

---

## Working 
```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#121212', 'primaryTextColor': '#ffffff', 'lineColor': '#888888' }}}%%
flowchart TD
    %% Node Styling Definitions (Dark Mode)
    classDef clientStyle fill:#01579b,stroke:#4fc3f7,stroke-width:2px,color:#e1f5fe
    classDef leaderStyle fill:#f57f17,stroke:#fff59d,stroke-width:2px,color:#fffde7
    classDef followerStyle fill:#1b5e20,stroke:#81c784,stroke-width:2px,color:#e8f5e9
    classDef candidateStyle fill:#e65100,stroke:#ffb74d,stroke-width:2px,color:#fff3e0
    classDef databaseStyle fill:#263238,stroke:#90a4ae,stroke-width:2px,color:#eceff1
    classDef processStyle fill:#4a148c,stroke:#ba68c8,stroke-width:2px,color:#f3e5f5
    classDef decisionStyle fill:#004d40,stroke:#4db6ac,stroke-width:2px,color:#e0f2f1

    %% Basic Client Server Topology
    subgraph Topology ["Basic Cluster Topology"]
        ClientNode["Client"]:::clientStyle -->|<b>gRPC PUT/GET</b>| Server2_Leader["Server 2 (Leader)"]:::leaderStyle
        Server2_Leader -->|<b>AppendEntries RPC</b>| Server1_Follower["Server 1 (Follower)"]:::followerStyle
        Server2_Leader -->|<b>AppendEntries RPC</b>| Server3_Follower["Server 3 (Follower)"]:::followerStyle
    end

    %% State Transitions
    subgraph State_Transitions ["Server Role Transitions"]
        FollowerRole["Follower"]:::followerStyle -->|"<b>Election Timeout</b><br>(no leader heartbeat)"| CandidateRole["Candidate"]:::candidateStyle
        CandidateRole -->|"<b>Election Timeout</b><br>(votes self, increments term, retries)"| CandidateRole
        CandidateRole -->|"<b>Wins Majority Votes</b>"| LeaderRole["Leader"]:::leaderStyle
        CandidateRole -->|"<b>Discovers Active Leader</b>"| FollowerRole
        LeaderRole -->|"<b>Discovers Higher Term</b>"| FollowerRole
    end

    %% Election Flow
    subgraph Election_Flow ["Election Flow (Phase 2)"]
        StartAsFollower["Start as Follower"]:::followerStyle --> RandTimeout["Randomized Timeout (300-600ms)"]:::processStyle
        RandTimeout --> FollowerToCand["Transition to Candidate"]:::candidateStyle
        FollowerToCand --> CandVotes["Votes self, increments term, broadcasts RequestVote RPC in parallel"]:::processStyle
        CandVotes --> EachServer["Each peer processes RequestVote request"]

        EachServer --> WinElec["(A) Candidate Wins Quorum"]
        EachServer --> AppendArr["(B) Candidate receives AppendEntries from active Leader"]
        EachServer --> ElecTO["(C) Timeout: Split Vote (No majority)"]

        WinElec --> SendEmpty["Leader broadcasts empty AppendEntries (Heartbeats) to establish authority"]:::leaderStyle

        AppendArr --> TermCompare{"Is Leader Term >= My Term?"}:::decisionStyle
        TermCompare -->|"<b>No: Reject & continue election</b>"| AppendArr
        TermCompare -->|"<b>Yes: Step down to Follower</b>"| AgreeLeader["Recognize Leader"]:::followerStyle

        AgreeLeader --> StartAsFollower
        ElecTO -->|"<b>Start New Election (Term + 1)</b>"| RandTimeout
    end

    %% Log Replication
    subgraph Log_Replication ["Log Replication (Phase 3)"]
        ClientLog["Client PUT request"]:::clientStyle --> LdrNode["Leader Node"]:::leaderStyle
        LdrNode --> AppendLog[("RaftLog WAL Disk File")]:::databaseStyle
        AppendLog --> FanOut["Broadcast AppendEntries RPC in parallel"]:::processStyle
        FanOut --> WaitAcks["Count Follower Acknowledgments"]

        WaitAcks --> NoMaj["Quorum NOT reached"]
        WaitAcks --> Maj["Quorum Reached (Majority)"]

        NoMaj -->|"<b>Retry (heartbeat loop)</b>"| FanOut
        Maj --> AdvanceCommit["Leader advances commitIndex"]:::processStyle
        AdvanceCommit --> ApplySM[("KV Database Map RAM")]:::databaseStyle
        ApplySM -->|"<b>Return success=true to Client</b>"| ClientLog
    end

    %% Java/gRPC Client Flow
    subgraph Client_Logic ["Client Logic & gRPC Redirection (Phase 4)"]
        CLI["CLI Command:<br>./gradlew :raft-client:run"]:::clientStyle --> BuildMap["Parse server list argument to serverMap"]
        BuildMap --> InitCh["Initialize ManagedChannel connection"]
        InitCh --> InitStub["Initialize KVService BlockingStub"]
        InitStub --> Ready["Ready to execute GET/PUT"]

        Ready --> ChooseSrv{"Is currentServerId set?"}:::decisionStyle
        ChooseSrv -->|"<b>No (-1)</b>"| PickRandom["Select first server from map"]
        ChooseSrv -->|"<b>Yes</b>"| ReqSrv["Send request to target server"]

        PickRandom --> ReqSrv
        ReqSrv -->|"<b>Network Timeout / Error</b>"| TryNext["Try next server in map"]
        TryNext --> InitCh

        ReqSrv --> CheckLdr{"Is server the Leader?"}:::decisionStyle
        CheckLdr -->|"<b>Yes (success=true)</b>"| ReqAck["Return success to console"]
        CheckLdr -->|"<b>No (success=false)</b>"| RetLdrId["Server returns known leaderId"]

        RetLdrId -->|"<b>Update currentServerId & Reconnect</b>"| InitCh
    end

    %% RaftNode Internal State Machine
    subgraph RaftNode_Internal ["RaftNode Engine Internal Logic"]
        RNode["RaftNode Instance"] -->|"<b>start()</b>"| ResetTimer["resetElectionTimer()"]
        RNode -->|"<b>stop()</b>"| StopTimer["stopElectionTimer() / stopHeartbeatTimer()"]

        ResetTimer --> IsLeaderCheck{"Is node the Leader?"}:::decisionStyle
        IsLeaderCheck -->|"<b>Yes</b>"| DoNothing["Keep sending heartbeats"]
        IsLeaderCheck -->|"<b>No</b>"| StartTimerSched["Schedule randomized background callback"]

        StartTimerSched -->|"<b>Callback Fired</b>"| RunElection["runElection(): currentTerm++, role=CANDIDATE, votedFor=self"]
        RunElection --> PersistState[("PersistentState WAL File")]:::databaseStyle
        PersistState --> SendVotes["Broadcast RequestVote RPCs in parallel"]

        %% Vote Request Logic
        SendVotes -->|"<b>Receives VoteRequest RPC</b>"| CheckVoteCond{"Evaluate Vote Request"}:::decisionStyle
        CheckVoteCond -->|"<b>Candidate Term < currentTerm</b>"| RejVote["Reply voteGranted=false"]
        CheckVoteCond -->|"<b>Candidate Term > currentTerm</b>"| StepDownFollower["stepDown(): term=candidateTerm, votedFor=-1, role=FOLLOWER"]

        StepDownFollower --> VoteAvailable{"Is votedFor empty (-1)<br>or CandidateId?"}:::decisionStyle
        VoteAvailable -->|"<b>No</b>"| RejVote
        VoteAvailable -->|"<b>Yes</b>"| LogUpToDate{"Is Candidate log at<br>least as up-to-date?"}:::decisionStyle

        LogUpToDate -->|"<b>No (stale term or shorter index)</b>"| RejVote
        LogUpToDate -->|"<b>Yes</b>"| AcceptVote["Set votedFor=candidateId, persistState(), resetElectionTimer()"]
        AcceptVote --> GrantVote["Reply voteGranted=true"]

        %% AppendEntry Logic
        RNode -->|"<b>Receives AppendEntries RPC</b>"| CheckAppCond{"Evaluate AppendEntries Request"}:::decisionStyle
        CheckAppCond -->|"<b>Leader Term < currentTerm</b>"| RejApp["Reply success=false<br>(return currentTerm & lastLogIndex)"]
        CheckAppCond -->|"<b>Leader Term >= currentTerm</b>"| VerifyFollowerRole{"Is role == CANDIDATE<br>or LEADER?"}:::decisionStyle

        VerifyFollowerRole -->|"<b>Yes</b>"| StepDownFollowerApp["stepDown() to Follower"]
        VerifyFollowerRole -->|"<b>No (already Follower)</b>"| ResetFollowerTimer["resetElectionTimer(), update leaderId"]

        StepDownFollowerApp --> ResetFollowerTimer
        ResetFollowerTimer --> LogCheck{"Does follower have entry at<br>prevLogIndex matching prevLogTerm?"}:::decisionStyle

        LogCheck -->|"<b>No (Log Mismatch)</b>"| RejApp
        LogCheck -->|"<b>Yes</b>"| WriteLogEntries["Append new entries to log<br>(overwriting conflicts) & persistState()"]

        WriteLogEntries --> CommitCheck{"Is leaderCommit > commitIndex?"}:::decisionStyle
        CommitCheck -->|"<b>Yes</b>"| SetFollowerCommit["Set commitIndex = min(leaderCommit, lastNewEntryIndex)"]
        CommitCheck -->|"<b>No</b>"| ReplyAppSuccess["Reply success=true (return matchIndex)"]

        SetFollowerCommit --> TriggerApply["applyLogEntries(): advance lastApplied,<br>trigger onCommit client callbacks"]
        TriggerApply --> ReplyAppSuccess
    end

    %% Subgraph Styling Definitions (Dark Mode)
    style Topology fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
    style State_Transitions fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
    style Election_Flow fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
    style Log_Replication fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
    style Client_Logic fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
    style RaftNode_Internal fill:#1e1e1e,stroke:#546e7a,stroke-width:2px,stroke-dasharray: 5 5,rx:10,ry:10,color:#ffffff
```

### Key Components inside each Node

Each node runs inside its own JVM process (or container) and has four main layers:
1. **gRPC Server / Services**: Exposes the remote API contracts. The server listens on `NODE_PORT` for incoming consensus RPCs (`RaftService`) and client requests (`KVService`).
2. **RaftNode Consensus Engine**: The core state machine governing term counters (`currentTerm`), voting history (`votedFor`), cluster election timers, and peer replication indexes. It is completely isolated from network transport interfaces.
3. **RaftLog & Disk WAL**: Manages the append-only log entries in memory (for fast reads) and syncs them to stable storage on disk (for recovery) before responding to RPCs.
4. **State Machine Database (KV Map)**: A local `ConcurrentHashMap` holding the final committed state of the data. Follower queries (GETs) read from this database directly, while writes (PUTs) must be committed by Raft consensus before being executed.

---

### Step-by-Step Request Flow (PUT)

Here is how a write request flows through the architecture:
1. **Client Submission**: The `RaftCliClient` sends a `PutRequest` over gRPC to what it believes is the leader (e.g. Node 1).
2. **Leadership Check**: If Node 1 is not the leader, it replies with `success=false` and points to the current leader's ID. The client automatically reconnects and redirects the request. If Node 1 is the leader, it accepts the write.
3. **Log Append**: The leader calls `RaftNode.clientWrite()`, appending the command (e.g. `PUT key value`) to its `RaftLog` (saved to disk).
4. **Replication Broadcast**: The leader's consensus engine broadcasts `AppendEntries` RPCs containing the new log entry to all peer nodes (Node 2 and Node 3) in parallel.
5. **Follower Acknowledgment**: Followers receive the entry, perform consistency checks, write it to their own disk logs, and return `success=true` to the leader.
6. **Quorum Commit**: Once the leader receives positive acknowledgments from a majority of nodes (quorum), it updates its `commitIndex` and calls `applyLogEntries()`.
7. **Database Apply & Client Reply**: The leader applies the command to its local KV map, resolves the client's pending future, and returns a successful response to the client.
8. **Follower Apply**: On the next heartbeat cycle, followers learn of the updated `commitIndex`, run the command on their local database maps, and advance their `lastApplied` index.

---

### Module Breakdown
- **`raft-core`**: Contains the pure Raft state machine (`RaftNode`), log management (`RaftLog`), and state storage (`PersistentState`). Has **zero** networking dependencies (no gRPC, no HTTP). It coordinates operations asynchronously via the `RaftNodeListener` interface.
- **`raft-rpc`**: Holds the Protocol Buffer contracts (`raft.proto`) and generated gRPC base services (`RaftServiceImpl`, `KVServiceImpl`).
- **`raft-server`**: The executable node orchestrator. It parses CLI/env configs (`ClusterConfig`), boots the gRPC server, instantiates peer client stubs (`PeerClient`), and wires the core `RaftNode` state machine to the transport layer.
- **`raft-client`**: A simple, interactive command-line client (`RaftCliClient`) for running GET/PUT operations on the cluster, featuring automatic leader redirection and server failover.

---

## Core Features & Safety Rules Implemented
- **Leader Election**: Randomized election timeouts (default 300–600ms) prevent split-vote cycles. Election safety is guaranteed by comparing last log terms and indices during voting.
- **Log Replication**: Outbound `AppendEntries` heartbeats sync logs. Follower mismatch replication conflicts are auto-corrected by backing off nextIndex.
- **Quorum Commits**: The leader only advances the `commitIndex` once an entry is replicated to a majority of nodes. To prevent old-term anomalies (Figure 8 in Raft paper), the leader only commits logs of its *current term* directly.
- **State Persistence**: Durability is guaranteed by writing the log and term metadata (`currentTerm`, `votedFor`) to disk. Safe file operations utilize a temp file and `java.nio.file.Files.move` with `ATOMIC_MOVE` to prevent file corruption during middle-of-write crashes.
- **Client Redirection & Failover**: Non-leader nodes redirect clients to the current leader. The client CLI automatically catches redirects and retries against the correct node.
- **Structured Logging**: Mapped Diagnostic Context (`MDC`) prints the current node ID and term with every log message, enabling clean multi-process cluster debugging.

---

## Getting Started

### Prerequisites
- **Java 21 (LTS)**
- **Docker** and **Docker Compose**

### Running with Docker Compose (Recommended)
You can spin up a 3-node cluster with one command. This builds the project inside a Gradle container first, then boots 3 slim containers connected on a bridge network.

```bash
# Start the cluster
docker-compose -f docker/docker-compose.yml up --build
```

Host port mapping:
- **Node 1**: `localhost:8001`
- **Node 2**: `localhost:8002`
- **Node 3**: `localhost:8003`

You will see logs detailing the election timeouts, candidate votes, and leader heartbeats:
```text
10:29:47.123 [main] INFO - [Node-1] RaftNode - Starting node 1 in state FOLLOWER
10:29:47.452 [scheduler-1] INFO - [Node-1] RaftNode - Election timeout on node 1, term 0, transitioning to CANDIDATE
10:29:47.453 [scheduler-1] INFO - [Node-1] RaftNode - Node 1 starting election for term 1
10:29:47.490 [main] INFO - [Node-1] RaftNode - Node 1 elected LEADER for term 1
```

---

## Using the CLI Client

The CLI client communicates with the cluster via gRPC. It loops through all servers, follows redirects, and retries other nodes if the current target fails.

Run the client using the pre-compiled Gradle task or command line:

```bash
# Syntax: ./gradlew :raft-client:run --args="<cluster-servers> <operation> <key> [value]"

# 1. Write a key-value pair to the cluster (will auto-route/redirect to the elected leader)
./gradlew :raft-client:run --args="1=localhost:8001,2=localhost:8002,3=localhost:8003 put mykey myvalue"

# 2. Read the key-value pair
./gradlew :raft-client:run --args="1=localhost:8001,2=localhost:8002,3=localhost:8003 get mykey"
```

Output:
```text
Sending PUT mykey=myvalue to Node 1...
REDIRECT: Node 1 is not the leader. Redirecting to Leader Node 2...
Sending PUT mykey=myvalue to Node 2...
SUCCESS: Put completed successfully.
```

---

## Testing

### Core Unit Tests
Test the core algorithm offline (e.g. mock timeouts and transitions with no network involved):
```bash
./gradlew :raft-core:test
```

---

## Key Configurations

Configurations can be customized via environment variables:

| Variable | Default | Description |
|---|---|---|
| `NODE_ID` | `1` | Numeric ID of the node |
| `NODE_PORT` | `8001` | gRPC server listening port |
| `PEERS` | *None* | Comma-separated peer list `nodeId=host:port` |
| `ELECTION_TIMEOUT_MIN_MS` | `300` | Minimum election timeout |
| `ELECTION_TIMEOUT_MAX_MS` | `600` | Maximum election timeout |
| `HEARTBEAT_INTERVAL_MS` | `50` | Heartbeat interval (AppendEntries rate) |
| `DATA_DIR` | `data/node<id>` | Local persistent state directory |
