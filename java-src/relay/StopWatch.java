package relay;

public class StopWatch {
    
    private static final long NOT_INITIALIZED = -1;
    
    private long start = NOT_INITIALIZED;
    private long stop = NOT_INITIALIZED;
    private boolean running = false;
    
    public StopWatch() {
        start = System.currentTimeMillis();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public static StopWatch startNew() {
        StopWatch w = new StopWatch();
        w.start();
        return w;
    }
    
    public StopWatch start() {
        if (running) {
            throw new IllegalStateException("Cannot start: StopWatch already started");
        }
        running = true;
        start = System.currentTimeMillis();
        return this;
    }
    
    public StopWatch restart() {
        running = true;
        stop = NOT_INITIALIZED;
        start = System.currentTimeMillis();
        return this;
    }
    
    public long stop() {
        if (!running) {
            throw new IllegalStateException("Cannot stop: StopWatch not running");
        }
        running = false;
        stop = System.currentTimeMillis();
        return stop - start;
    }
    
    public long getElapsedMillis() {
        if (running) {
            return System.currentTimeMillis() - start;
        }
        return stop - start;
    }
    
    public double getElapsedSeconds() {
        return getElapsedMillis() / 1000.0;
    }

}
