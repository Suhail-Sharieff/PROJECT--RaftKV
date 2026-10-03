package org.example.core.enums;

public enum NodeState {
    FOLLOWER,
    CANDIDATE,
    LEADER
}

//every node starts as a follower and upon election timeout, it starts election, if somebody asks vote ie VoteRequest arrives, since it hasn't voted anyone, it votes that candidate and moves to follower state, if none asks vote yet, it votes for itself and sends RequestVote RPC to other nodes, if some node gets majority of votes, it becomes leader by incrementing its term and sends heartbeat to other nodes, who become followers
