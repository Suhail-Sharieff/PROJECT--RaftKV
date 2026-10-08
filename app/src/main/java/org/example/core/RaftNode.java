package org.example.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import org.example.core.enums.LogEntry;
import org.example.core.enums.NodeState;
import org.example.core.listeners.RaftNodeListener;
import org.example.core.logManagement.RaftLog;
import org.example.core.logManagement.persitence.PersistentState;
import org.example.core.netwrokParams.AppendEntriesRequest;
import org.example.core.netwrokParams.AppendEntriesResponse;
import org.example.core.netwrokParams.VoteRequest;
import org.example.core.netwrokParams.VoteResponse;
import org.example.core.timer.ElectionTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    private final int nodeId;
    private final List<Integer> peerIds; // ID of other nodes in the cluster
    private final ReentrantLock lock = new ReentrantLock();

    // Persistent state on all servers (or in-memory for Phase 1)
    private final PersistentState persistentState;
    private long currentTerm = 0;
    private int votedFor = -1;
    private final RaftLog logManager;

    // Volatile state on all servers
    private int commitIndex = 0;
    private int lastApplied = 0;

    // Volatile state on leaders (reinitialized after election)
    private final Map<Integer, Integer> node_nextIndex = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> node_matchIndex = new ConcurrentHashMap<>();

    // Consensus role & leader tracking
    private NodeState nodeState = NodeState.FOLLOWER;
    private int leaderId = -1;
    private final Set<Integer> votesReceived = new ConcurrentHashSet<>();

    // Timers & Schedulers
    private final ScheduledExecutorService scheduler;
    private final RaftNodeListener listener;
    private final ElectionTimer electionTimer;
    private ScheduledFuture<?> heartbeatFuture;

    private final int minElectionTimeoutMs;
    private final int maxElectionTimeoutMs;
    private final int heartbeatIntervalMs;

    public RaftNode(
            int nodeId,
            List<Integer> peerIds,
            ScheduledExecutorService scheduler,
            RaftNodeListener listener,
            int minElectionTimeoutMs,
            int maxElectionTimeoutMs,
            int heartbeatIntervalMs
    ) {
        this(nodeId, peerIds, scheduler, listener, minElectionTimeoutMs, maxElectionTimeoutMs, heartbeatIntervalMs, null);
    }

    public RaftNode(
            int nodeId,
            List<Integer> peerIds,
            ScheduledExecutorService scheduler,
            RaftNodeListener listener,
            int minElectionTimeoutMs,
            int maxElectionTimeoutMs,
            int heartbeatIntervalMs,
            PersistentState persistentState
    ) {
        this.nodeId = nodeId;
        this.peerIds = new ArrayList<>(peerIds);
        this.scheduler = scheduler;
        this.listener = listener;
        this.minElectionTimeoutMs = minElectionTimeoutMs;
        this.maxElectionTimeoutMs = maxElectionTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.persistentState = persistentState;
        this.logManager = new RaftLog(persistentState);

        if (persistentState != null) {
            this.currentTerm = persistentState.getCurrentTerm();
            this.votedFor = persistentState.getVotedFor();
            this.logManager.reset(persistentState.getLogEntries());
            logger.info("Node {} loaded persisted state: term={}, votedFor={}, log={}", nodeId, currentTerm, votedFor, logManager);
        }

        this.electionTimer = new ElectionTimer(scheduler, this::onElectionTimeout, minElectionTimeoutMs, maxElectionTimeoutMs);
    }

    public void start() {
        lock.lock();
        try {
            logger.info("Starting node {} in state {}", nodeId, nodeState);
            resetElectionTimer();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            logger.info("Stopping node {}", nodeId);
            electionTimer.stop();
            stopHeartbeatTimer();
        } finally {
            lock.unlock();
        }
    }

    // --- Core Timer Handlers ---

    private void onElectionTimeout() {
        lock.lock();
        try {
            if (nodeState == NodeState.LEADER) {
                return;
            }
            logger.info("Election timeout on node {}, term {}, transitioning to CANDIDATE", nodeId, currentTerm);
            startElection();
        } finally {
            lock.unlock();
        }
    }

    private void startElection() {
        NodeState oldState = nodeState;
        nodeState = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;//votes itself first
        votesReceived.clear();
        votesReceived.add(nodeId);

        logger.info("Node {} starting election for term {}", nodeId, currentTerm);
        listener.onStateChange(oldState, nodeState, currentTerm);
        persistState();
        resetElectionTimer();

        int totalNodes = peerIds.size() + 1;
        int quorum = totalNodes / 2 + 1;

        if (votesReceived.size() >= quorum) {
            becomeLeader();
            return;
        }

        long lastLogIndex = logManager.getLastLogIndex();
        long lastLogTerm = logManager.getLastLogTerm();

        for (int peer : peerIds) {
            listener.sendRequestVote(peer, currentTerm, nodeId, lastLogIndex, lastLogTerm);
        }
    }

    private void becomeLeader() {
        NodeState oldState = nodeState;
        nodeState = NodeState.LEADER;
        leaderId = nodeId;
        logger.info("Node {} elected LEADER for term {}", nodeId, currentTerm);
        listener.onStateChange(oldState, nodeState, currentTerm);

        electionTimer.stop();

        // Initialize leader state
        node_nextIndex.clear();
        node_matchIndex.clear();
        for (int peer : peerIds) {
            node_nextIndex.put(peer, logManager.getLastLogIndex() + 1);
            node_matchIndex.put(peer, 0);
        }

        // Send initial heartbeats
        sendHeartbeats();

        // Start heartbeat schedule
        startHeartbeatTimer();
    }

    private void startHeartbeatTimer() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                if (nodeState == NodeState.LEADER) {
                    sendHeartbeats();
                }
            } catch (Exception e) {
                logger.error("Error running heartbeat task", e);
            } finally {
                lock.unlock();
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeatTimer() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void sendHeartbeats() {
        long commitIndexVal = commitIndex;
        for (int peer : peerIds) {
            sendAppendEntriesToPeer(peer);
        }
    }

    private void sendAppendEntriesToPeer(int peerId) {
        int commitIndexVal = commitIndex;
        int nextIdx = node_nextIndex.getOrDefault(peerId, logManager.getLastLogIndex() + 1);
        int prevLogIndex = nextIdx - 1;
        long prevLogTerm = logManager.getTermOfEntryAtIndex(prevLogIndex);
        List<LogEntry> entries = logManager.getEntriesStartingFrom(nextIdx);
        listener.sendAppendEntries(peerId, currentTerm, nodeId, prevLogIndex, prevLogTerm, entries, commitIndexVal);
    }

    private void resetElectionTimer() {
        electionTimer.reset();
    }

    // --- RPC Handlers ---

    public VoteResponse handleRequestVote(VoteRequest req) {
        lock.lock();
        try {
            if (req.term() < currentTerm) {
                return new VoteResponse(currentTerm, false);
            }

            if (req.term() > currentTerm) {
                stepDown(req.term());
            }

            boolean logUpToDate = false;
            long lastLogTerm = logManager.getLastLogTerm();
            long lastLogIndex = logManager.getLastLogIndex();
            if (req.lastLogTerm() > lastLogTerm) {
                logUpToDate = true;
            } else if (req.lastLogTerm() == lastLogTerm) {
                logUpToDate = req.lastLogIndex() >= lastLogIndex;
            }

            if ((votedFor == -1 || votedFor == req.candidateId()) && logUpToDate) {
                votedFor = req.candidateId();
                persistState();
                resetElectionTimer();
                logger.info("Node {} voted for candidate {} in term {}", nodeId, req.candidateId(), currentTerm);
                return new VoteResponse(currentTerm, true);
            }

            return new VoteResponse(currentTerm, false);
        } finally {
            lock.unlock();
        }
    }

    public void handleRequestVoteResponse(int peerId, long term, boolean voteGranted) {
        lock.lock();
        try {
            if (term > currentTerm) {
                stepDown(term);
                return;
            }

            if (nodeState != NodeState.CANDIDATE || term != currentTerm) {
                return;
            }

            if (voteGranted) {
                votesReceived.add(peerId);
                int totalNodes = peerIds.size() + 1;
                int quorum = totalNodes / 2 + 1;
                if (votesReceived.size() >= quorum) {
                    becomeLeader();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req) {
        lock.lock();
        try {
            // Rule 1: Reply false if term < currentTerm
            if (req.term() < currentTerm) {
                return new AppendEntriesResponse(currentTerm, false, logManager.getLastLogIndex());
            }

            if (req.term() > currentTerm || (nodeState == NodeState.CANDIDATE && req.term() == currentTerm)) {
                stepDown(req.term());
            }

            if (nodeState == NodeState.FOLLOWER) {
                leaderId = req.leaderId();
                resetElectionTimer();
            }

            // Rule 2: Reply false if log doesn't contain an entry at prevLogIndex matching prevLogTerm
            if (req.prevLogIndex() > 0) {
                if (!logManager.hasEntryAt(req.prevLogIndex(), req.prevLogTerm())) {
                    // Match fail; return false with last log index to help leader back off
                    return new AppendEntriesResponse(currentTerm, false, logManager.getLastLogIndex());
                }
            }

            // Rule 3 & 4: Process entry additions/overwrites
            int lastNewEntryIndex = req.prevLogIndex();
            if (req.logEntries() != null) {
                for (LogEntry entry : req.logEntries()) {
                    logManager.appendLogEntry(entry);
                    lastNewEntryIndex = entry.index();
                }
                persistState();
            }

            // Rule 5: If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, lastNewEntryIndex)
            if (req.leaderCommit() > commitIndex) {
                commitIndex = Math.min(req.leaderCommit(), lastNewEntryIndex);
                logger.info("Follower {} advanced commitIndex to {}", nodeId, commitIndex);
            }

            return new AppendEntriesResponse(currentTerm, true, lastNewEntryIndex);
        } finally {
            lock.unlock();
            applyLogEntries();
        }
    }

    public void handleAppendEntriesResponse(int peerId, long term, boolean success, int matchIndexVal) {
        lock.lock();
        try {
            if (term > currentTerm) {
                stepDown(term);
                return;
            }

            if (nodeState != NodeState.LEADER || term != currentTerm) {
                return;
            }

            if (success) {
                node_nextIndex.put(peerId, matchIndexVal + 1);
                node_matchIndex.put(peerId, matchIndexVal);

                // Update commitIndex if majority has replicated it in currentTerm
                int lastLogIndex = logManager.getLastLogIndex();
                for (int N = lastLogIndex; N > commitIndex; N--) {
                    if (logManager.getTermOfEntryAtIndex(N) == currentTerm) {
                        int count = 1; // Count leader itself
                        for (int peer : peerIds) {
                            if (node_matchIndex.getOrDefault(peer, 0) >= N) {
                                count++;
                            }
                        }
                        int totalNodes = peerIds.size() + 1;
                        if (count >= (totalNodes / 2 + 1)) {
                            commitIndex = N;
                            logger.info("Leader {} advanced commitIndex to {} based on peer acknowledgements", nodeId, commitIndex);
                            break;
                        }
                    }
                }
            } else {
                // Back off nextIndex
                int currentNext = node_nextIndex.getOrDefault(peerId, 1);
                int next = Math.max(1, Math.min(currentNext - 1, matchIndexVal + 1));
                node_nextIndex.put(peerId, next);

                // Retry AppendEntries immediately
                sendAppendEntriesToPeer(peerId);
            }
        } finally {
            lock.unlock();
            applyLogEntries();
        }
    }

    // --- Client Interaction APIs ---

    /**
     * Submit a write request to the leader.
     * Returns the log index assigned to this entry, or -1 if the node is not leader.
     */
    public long clientWrite(String command) {
        lock.lock();
        try {
            if (nodeState != NodeState.LEADER) {
                return -1;
            }
            long index = logManager.appendLogEntry(currentTerm, command);
            logger.info("Leader {} appended client command: '{}' at index {}", nodeId, command, index);
            persistState();

            // Broadcast the new entry immediately
            sendHeartbeats();
            return index;
        } finally {
            lock.unlock();
        }
    }

    // --- Helper Methods ---

    private void stepDown(long newTerm) {
        NodeState oldState = nodeState;
        nodeState = NodeState.FOLLOWER;
        currentTerm = newTerm;
        votedFor = -1;
        leaderId = -1;
        persistState();
        resetElectionTimer();
        stopHeartbeatTimer();
        logger.info("Node {} stepping down to Follower in term {}", nodeId, currentTerm);
        listener.onStateChange(oldState, nodeState, currentTerm);
    }

    private void applyLogEntries() {
        List<LogEntry> entriesToApply = new ArrayList<>();
        List<Integer> indicesToApply = new ArrayList<>();
        lock.lock();
        try {
            while (commitIndex > lastApplied) {
                lastApplied++;
                LogEntry entry = logManager.getLogEntryAt(lastApplied);
                if (entry != null) {
                    entriesToApply.add(entry);
                    indicesToApply.add(lastApplied);
                }
            }
        } finally {
            lock.unlock();
        }

        // Invoke callbacks outside the lock to prevent deadlock
        for (int i = 0; i < entriesToApply.size(); i++) {
            listener.onCommit(indicesToApply.get(i), entriesToApply.get(i));
        }
    }

    private void persistState() {
        if (persistentState != null) {
            persistentState.updateMetadata(currentTerm, votedFor);
        }
    }

    // --- State Inspection Getters ---

    public NodeState getNodeState() {
        lock.lock();
        try {
            return nodeState;
        } finally {
            lock.unlock();
        }
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getLeaderId() {
        lock.lock();
        try {
            return leaderId;
        } finally {
            lock.unlock();
        }
    }

    public long getCurrentTerm() {
        lock.lock();
        try {
            return currentTerm;
        } finally {
            lock.unlock();
        }
    }

    public long getCommitIndex() {
        lock.lock();
        try {
            return commitIndex;
        } finally {
            lock.unlock();
        }
    }

    public long getLastApplied() {
        lock.lock();
        try {
            return lastApplied;
        } finally {
            lock.unlock();
        }
    }

    public RaftLog getLogManager() {
        return logManager;
    }

    public int getVotedFor() {
        lock.lock();
        try {
            return votedFor;
        } finally {
            lock.unlock();
        }
    }

    // Helper set for concurrent votes
    private static class ConcurrentHashSet<E> extends AbstractSet<E> {
        private final ConcurrentHashMap<E, Boolean> map = new ConcurrentHashMap<>();

        @Override
        public boolean add(E e) {
            return map.put(e, Boolean.TRUE) == null;
        }

        @Override
        public boolean remove(Object o) {
            return map.remove(o) != null;
        }

        @Override
        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public Iterator<E> iterator() {
            return map.keySet().iterator();
        }

        @Override
        public int size() {
            return map.size();
        }
    }
}