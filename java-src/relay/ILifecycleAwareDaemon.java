package relay;

public interface ILifecycleAwareDaemon extends ILifecycleAware, Runnable {
    
    public abstract String getName();

}
