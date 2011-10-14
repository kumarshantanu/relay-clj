package relay;

import relay.DaemonState.DaemonStateEnum;

public abstract class AbstractLifecycleAwareDaemon implements ILifecycleAwareDaemon {
    
    private final String name;
    private DaemonState daemonState = new DaemonState();
    
    public AbstractLifecycleAwareDaemon(final String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name==null? getClass().getSimpleName(): name.toString();
    }
    
    public DaemonStateEnum getState() {
        synchronized (daemonState) {
            return daemonState.get();
        }
    }
    
    protected void setState(DaemonStateEnum newState) {
        synchronized (daemonState) {
            daemonState.set(newState);
        }
    }
    
    public void resume() {
        synchronized (daemonState) {
            if (daemonState.isResumable()) {
                daemonState.set(DaemonStateEnum.RUNNING);
            }
        }
    }
    
    public void stop() {
        synchronized (daemonState) {
            if (daemonState.isStoppable()) {
                daemonState.set(DaemonStateEnum.STOPPED);
            }
        }
    }
    
    public void forceStop() {
        synchronized (daemonState) {
            if (daemonState.isStoppable()) {
                daemonState.set(DaemonStateEnum.FORCE_STOPPED);
            }
        }
    }
    
    public void suspend() {
        synchronized (daemonState) {
            if (daemonState.isSuspendable()) {
                daemonState.set(DaemonStateEnum.SUSPENDED);
            }
        }
    }
    
    public void run() {
        setState(DaemonStateEnum.RUNNING);
        try {
            execute();
        } finally {
            setState(DaemonStateEnum.STOPPED);
        }
    }
    
    public abstract void execute();
    
    @Override
    public String toString() {
        return name==null? this.getClass().getName(): name.toString();
    }
    
}