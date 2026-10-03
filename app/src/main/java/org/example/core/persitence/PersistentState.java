package org.example.core.persitence;
//so basically v know that we store LogEntries into a WAL file that needs to be persistent so that it cud be recovered upon node shutdown, so this class is responsible for storing LogEntries into persistent disk storage


import org.example.core.enums.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
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
        this.metaDataFile=new File(dir,"metaData");//metaDataFile
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
}
