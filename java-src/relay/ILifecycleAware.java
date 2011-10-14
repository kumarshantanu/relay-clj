package relay;

import relay.DaemonState.DaemonStateEnum;

public interface ILifecycleAware {
    
    public DaemonStateEnum getState();
    
    public void suspend();
    
    public void resume();
    
    public void stop();
    
    public void forceStop();
    
}