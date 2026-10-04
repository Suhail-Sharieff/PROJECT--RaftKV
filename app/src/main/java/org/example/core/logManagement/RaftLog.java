package org.example.core.logManagement;

import org.example.core.enums.LogEntry;
import org.example.core.logManagement.persitence.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


//PersistentState provided us the APIs to just handle appendEntries and truncate Entries and get and set metadata from disk
//but this class provides broader APIs to interact with LogEntries, this class also manages our PersistentState
//this class u can say is main head of log entry control
//log entries we will use 1 based indexing
public class RaftLog {
    private final List<LogEntry> logEntries=new ArrayList<>();
    private final PersistentState persistentState;
    RaftLog(PersistentState persistentState){this.persistentState=persistentState;this.logEntries.add(new LogEntry(0,0,""));//v hv inserted some dummy entries so we can get 1 based indexing
    }
    public synchronized int getLastLogIndex(){
        //index of the last log entry inserted
        return logEntries.size()-1;
    }
    public synchronized  long getLastLogTerm(){
        return logEntries.getLast().term();
    }
    public synchronized  long getTermOfEntryAtIndex(int idx){
        return logEntries.get(idx).term();
    }
    public synchronized LogEntry getLogEntryAt(int idx){
        return  logEntries.get(idx);
    }
    public synchronized boolean hasEntryAt(int idx,long term){
        if(idx<0 || idx>=logEntries.size()) return  false;
        //check if any entry exists at that idx and ALSO having that term
        return  logEntries.get(idx).term()==term;
    }
    public synchronized List<LogEntry> getEntriesStartingFrom(int idx){//1 based indexing needed
        if(idx<=0 || idx>=logEntries.size()) return Collections.emptyList();
        return  new ArrayList<>(logEntries.subList(idx,logEntries.size()));
    }
    public synchronized int appendLogEntry(long term,String command){
        int nextIdx=logEntries.size();
        LogEntry entry=new LogEntry(term,nextIdx,command);
        logEntries.add(entry);
        persistentState.appendNewEntry(entry);
        return nextIdx;
    }
    public synchronized  void appendLogEntry(LogEntry entry){
        if(entry.index()==logEntries.size()){
            logEntries.add(entry);
            persistentState.appendNewEntry(entry);
        }else if(entry.index()<logEntries.size()){
            //then we need to truncate log entries after that index
            //when it can happen:  If a leader started writing entries but crashed before committing them, those entries are invalid. A new leader is now sending us the correct entries for those same indexes.
            LogEntry existing = logEntries.get((int) entry.index());
            if (existing.term() != entry.term()) {
                truncate(entry.index());
                logEntries.add(entry);
                if (persistentState != null) {
                    persistentState.appendNewEntry(entry);
                }
            }//else it's just a duplicate network command, so ignore it
        }else throw new IllegalStateException("Cannot append entry with index " + entry.index() + " to log of size " + logEntries.size());
    }

    private void truncate(int fromIndex) {
        while(logEntries.size()>fromIndex) logEntries.removeLast();
        persistentState.truncateLogs(fromIndex);
    }


    public synchronized int size() {
        return logEntries.size();
    }

    public synchronized void reset(List<LogEntry> newEntries) {
        logEntries.clear();
        logEntries.add(new LogEntry(0, 0, ""));
        for (LogEntry entry : newEntries) {
            if (entry.index() > 0) {
                logEntries.add(entry);
            }
        }
    }

    @Override
    public synchronized String toString() {
        return "RaftLog{" +
                "size=" + (logEntries.size() - 1) +
                ", lastIndex=" + getLastLogIndex() +
                ", lastTerm=" + getLastLogTerm() +
                '}';
    }
}
