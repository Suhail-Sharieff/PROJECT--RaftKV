package org.example.core.timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

//this class is responsible for handling heartbeats and election time out callbacks
public class ElectionTimer {
    private final Logger logger=LoggerFactory.getLogger(ElectionTimer.class);
    //we can use ScheduledExecutorService to start a thread in bg to track for heartbeats and requests, the thread will call callback function upon timeout
    private final ScheduledExecutorService scheduler;
    private final Runnable callback;
    //we need to have random timeouts between all nodes so all nodes DONT conduct election at a time, so a random timeout is chosen between minTimout and maxTimeout
    private final int minTimeoutMs,maxTimeoutms;
    private final Random rand=new Random();
    private ScheduledFuture<?>future;//scheduler.schedule(<callback>,<delay>,<timeout>) returns ScheduledFuture, ie the task thats running, so we can cancel it whenever needed


    public ElectionTimer(ScheduledExecutorService scheduler, Runnable callback, int minTimeoutMs, int maxTimeoutms) {
        this.scheduler = scheduler;
        this.callback = callback;
        this.minTimeoutMs = minTimeoutMs;
        this.maxTimeoutms = maxTimeoutms;
    }

    public  synchronized void reset(){
        if(future!=null) future.cancel(false);
        int randTimeout=rand.nextInt(minTimeoutMs,maxTimeoutms+1);
        //schedule task
        this.future=scheduler.schedule(
                ()->{
                    try{
                        callback.run();
                    }catch (Exception e){
                        logger.error("not able to schdule election timeout by scheduler");
                    }
                },
                randTimeout,
                TimeUnit.MILLISECONDS
        );
    }
    public synchronized void stop(){
        if(future!=null) {
            future.cancel(false);
            future=null;
        }
    }
}
