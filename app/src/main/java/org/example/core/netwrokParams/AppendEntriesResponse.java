package org.example.core.netwrokParams;

public record AppendEntriesResponse(long term,boolean success,int matchIndex) {
}
