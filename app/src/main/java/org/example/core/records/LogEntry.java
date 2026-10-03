package org.example.core.records;

public record LogEntry(long term,int index,String command) {
}
//so basically LogEntry defines the schema of each entry tat we wud be storing on our log file,
//each node maintains its own Log file having LogEntries
// obviously it needs to be immutable so we keep it as record, it needs detail about term of candidate, index of current entry and what was that command