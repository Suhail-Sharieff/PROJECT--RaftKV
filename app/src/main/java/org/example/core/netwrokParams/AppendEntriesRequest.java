package org.example.core.netwrokParams;

import org.example.core.enums.LogEntry;

import java.util.List;

public record AppendEntriesRequest(
        int leaderId,
        long term,
        int prevLogIndex,
        long prevLogTerm,
        int leaderCommit,
        List<LogEntry> logEntries
) {
}
