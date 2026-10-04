package org.example.core.logManagement.persitence;
//so basically v know that we store LogEntries into a WAL file that needs to be persistent so that it cud be recovered upon node shutdown, so this class is responsible for storing LogEntries into persistent disk storage


import org.example.core.enums.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class PersistentState {
    private static final Logger logger = LoggerFactory.getLogger(PersistentState.class);
    //so we need to store 2 things, 1 about election metadata like term and votedFor and other is actual LogEntries, so v will need 2 files
    private final File metaDataFile;//just stores currentTerm and votedFor
    private final File logFile;

    //so everytime APIs of this class are queried, it wud simply need to read from disk, which can be quite slow, we we will try to cache things instead in the form of below data structures and serve from cache in WriteAlongCache style to speed up things
    //------cache items of metaDataFile
    private long currentTerm=0;
    private int votedFor=-1;
    //-----cache items of logFile
    List<LogEntry> logEntries=new ArrayList<>();


    public PersistentState(String dataDir) {//we will create metaDataFile and log file into this directory
        File dir=new File(dataDir);
        if(!dir.exists()) dir.mkdirs();
        this.metaDataFile=new File(dir,"metadata.state");//metaDataFile
        this.logFile=new File(dir,"raft.log");//log file
        //so now for first time when server starts all these files and folders are created IF THEY DON'T EXIST ONLY

        //now load cache by reading from disk
        load_cache_from_metaDataFile();
        load_cache_from_logFile();

    }

    private void load_cache_from_logFile() {
        if(!logFile.exists()) return;
        try(BufferedReader reader=new BufferedReader(new FileReader(logFile))){
            logEntries.clear();//safety to clear bfr loading
            String line;
            while((line=reader.readLine())!=null){
                String arr[]=line.split(":");//coz they are in format <term>:<index>:<command>
                long term=Long.parseLong(arr[0]);
                int index=Integer.parseInt(arr[1]);
                String command = new String(Base64.getDecoder().decode(arr[2]), StandardCharsets.UTF_8);//coz its
                logEntries.add(new LogEntry(term,index,command));
            }
        }catch (Exception e){logger.error("failed to load log file file content into cache",e);}
    }

    private void load_cache_from_metaDataFile() {
        if(!metaDataFile.exists()) return;
        try(BufferedReader reader=new BufferedReader(new FileReader(metaDataFile))){
            String line;
            while((line=reader.readLine())!=null){
                if(line.startsWith("term")) currentTerm=Long.parseLong(line.substring(5).trim());
                else if(line.startsWith("votedFor")) votedFor=Integer.parseInt(line.substring(9).trim());

            }
        }catch (Exception e){logger.error("failed to load metaData file content into cache",e);}
    }



    //------utilities method, APIs it provides to other classes, all are thread safe---->handles concurrency issues

    public synchronized long getCurrentTerm() {
        return currentTerm;
    }

    public synchronized  int getVotedFor() {
        return votedFor;
    }

    public synchronized  List<LogEntry> getLogEntries() {
        return new ArrayList<>(logEntries);//return deep copy, so tht even if modified doesnt harm us
    }

    //so when some leader wins and sends heartbeats, or this node votes for someone, this node needs to update its metaData in both cache and disk
    public synchronized void updateMetadata(long newTerm,int newVotedFor){
        //update in cache
        this.currentTerm=newTerm;
        this.votedFor=newVotedFor;
        //update in disk
        //but this op shud happen atomically, ie both term and votedFor shud be updated at once and not just one, so we can use concept of Atomic Swap with temp file
        File tempMetaDataFile=new File(metaDataFile.getParentFile(),"metadata.state.tmp");
        //append new term and voted for in temp file
        try(PrintWriter writer=new PrintWriter(new FileWriter(tempMetaDataFile))){
            writer.println("term="+newTerm);
            writer.println("votedFor="+votedFor);
            writer.flush();
            //now atomic swap original file with temp file
            Files.move(tempMetaDataFile.toPath(),metaDataFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("failed to save new metadata state into temp file",e);
        }
    }
    //when leader asks to append new entry
    public synchronized void appendNewEntry(LogEntry newEntry){
        //add in cache
        logEntries.add(newEntry);
        //persist in disk
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String base64Command = Base64.getEncoder().encodeToString(newEntry.command().getBytes(StandardCharsets.UTF_8));
            writer.println(newEntry.term() + ":" + newEntry.index() + ":" + base64Command);
        } catch (IOException e) {
            logger.error("Failed to new log entry to file", e);
        }
    }


    //sometimes due to inconsistencies the leader may want this node to delete some troublesome log entries, tis method handles tat
    public synchronized void truncateLogs(long fromIndex) {
        if (fromIndex <= 0) return;

        while (!logEntries.isEmpty() && logEntries.getLast().index() >= fromIndex) {
            logEntries.removeLast();
        }

        File tempFile = new File(logFile.getParentFile(), "raft.log.tmp");
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            for (LogEntry entry : logEntries) {
                String base64Command = Base64.getEncoder().encodeToString(entry.command().getBytes(StandardCharsets.UTF_8));
                writer.println(entry.term() + ":" + entry.index() + ":" + base64Command);
            }
            writer.flush();
            Files.move(tempFile.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to truncate and rewrite log file", e);
        }
    }
}
