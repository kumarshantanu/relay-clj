package relay;

public class DaemonState {
    
    public static enum DaemonStateEnum {
        READY,
        RUNNING,
        SUSPENDED,
        STOPPED,
        FORCE_STOPPED  // temporary state - convert to STOPPED after operation
    }
    
    private volatile DaemonStateEnum internalState = DaemonStateEnum.READY;
    
    public synchronized void set(final DaemonStateEnum newState) {
        internalState = newState;
    }
    
    public synchronized DaemonStateEnum get() {
        return internalState;
    }
    
    public synchronized boolean isResumable() {
        return (internalState == DaemonStateEnum.SUSPENDED)? true: false;
    }
    
    public synchronized boolean isSuspendable() {
        return internalState == DaemonStateEnum.RUNNING? true: false;
    }
    
    public synchronized boolean isStoppable() {
        return (internalState == DaemonStateEnum.RUNNING ||
                internalState == DaemonStateEnum.SUSPENDED)? true: false;
    }
    
}