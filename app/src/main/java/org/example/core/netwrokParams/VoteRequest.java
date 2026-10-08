package org.example.core.netwrokParams;

public record VoteRequest(int candidateId,long term,int lastLogIndex,long lastLogTerm) {
}
