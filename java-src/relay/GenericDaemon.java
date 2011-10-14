package relay;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import relay.DaemonState.DaemonStateEnum;
import clojure.lang.IFn;
import clojure.lang.ISeq;

public class GenericDaemon extends AbstractLifecycleAwareDaemon {
    
    /**
     * No-argument function that is called as the service
     */
    public final IFn service;
    
    /**
     * Duration in milliseconds that the daemon must sleep when idle
     */
    public final long idleMillis;
    
    public final int parallelJobs;
    public final ExecutorService pool;
    public final BlockingQueue<Future<?>> jobs;
    public final IFn argsMaker;
    public final IFn collector;
    
    public GenericDaemon(String name, IFn service, long idleMillis,
            ExecutorService pool, int parallelJobs,
            IFn argsMaker, IFn collector) {
        super(name);
        this.service = service;
        this.idleMillis = idleMillis;
        this.pool = pool;
        this.parallelJobs = parallelJobs;
        this.jobs = new LinkedBlockingQueue<Future<?>>(parallelJobs);
        this.argsMaker = argsMaker;
        this.collector = collector;
    }

    protected void error(Exception e) {
        e.printStackTrace();
        this.suspend();
    }
    
    @Override
    public void execute() {
        EXECUTE_LOOP: while (true) {
            // pump out completed jobs from job queue
            for (Iterator<Future<?>> it = jobs.iterator(); it.hasNext();) {
                Future<?> job = it.next();
                if (job.isDone()) {
                    it.remove();
                    try {
                        Object result = job.get();
                        try {
                            collector.invoke(this, result);
                        } catch (Exception e) {
                            error(e);
                            continue EXECUTE_LOOP;
                        }
                    } catch (CancellationException e) {
                        // job was cancelled, so ignore exception
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause != null) {
                            if (cause instanceof Exception) {
                                error((Exception) cause);
                                continue EXECUTE_LOOP;
                            } else {
                                RuntimeException r = new RuntimeException(cause);
                                error(r);
                                throw r;
                            }
                        }
                    }
                } else {
                    break;
                }
            }
            
            // honour daemon-state
            if (getState() == DaemonStateEnum.STOPPED && jobs.isEmpty()) {
                break;
            }
            if (getState() == DaemonStateEnum.FORCE_STOPPED) {
                setState(DaemonStateEnum.STOPPED);
                break;
            }
            while (getState() == DaemonStateEnum.SUSPENDED) {
                TimerUtil.sleep(idleMillis);
                continue EXECUTE_LOOP;
            }
            
            // submit next job
            if (jobs.size() >= parallelJobs) {
                TimerUtil.sleep(idleMillis);
            } else {
                try {
                    final ISeq arglist = (ISeq) argsMaker.invoke(this);
                    if (arglist != null) {
                        Callable<?> task = new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                return service.applyTo(arglist);
                            }
                        };
                        Future<?> job = pool.submit(task);
                        jobs.add(job);
                    }
                } catch (Exception e) {
                    error(e);
                }
            }
            
            
//            try {
//                service.invoke(this);
//            } catch (final Exception e) {
//                e.printStackTrace();
//                this.suspend();
//            }
        }
    }

}
