# relay

A Clojure library to create data-processing pipelines using `daemons` and
concurrent `queues`, typically to work in a producer-consumer fashion.

* Every `daemon` runs in its own thread and is lifecycle-aware, i.e. can
  transition between states: READY, RUNNING, SUSPENDED, STOPPED.
* A `daemon` asynchronously runs a specified function in a loop until it is
  SUSPENDED or STOPPED. It can be configured to run the function in multiple
  threads in parallel.
* The `daemon` can be optionally configured with `args-maker` and `collector`
  functions that are guaranteed to run in a FIFO order together. These functions
  can be used to read from and write to queues.
* This library creates in-JVM queues only; for remote queues you can use an
  external provider (e.g. RabbitMQ, HornetQ, Beanstalkd etc.) and have the queue
  wrapped in java.util.concurrent.BlockingQueue (preferred) or java.util.Queue -
  the daemons will work similarly as with in-JVM queues.


## Usage

Refer to the `relay.core` namespace as appropriate:

    (ns foo.bar
      (:require [relay.core :as relay]))


`make-daemon` creates a daemon, which is initially in READY state.

`make-queue` creates an in-JVM concurrent queue.

`args-maker-inbox` creates an args-maker function to read from an inbox queue.

`wrap-args-maker-max-size` wraps an args-maker function to honor max-size of a
collection (typically an outbox queue, so as not to overload it.)

`collector-outbox` creates a collector function to write to an outbox queue.

`make-cached-thread-pool`, `make-fixed-thread-pool` and `make-single-thread-pool`
create thread pools. `unbounded-thread-pool` is a var bound to a thread pool
created using `make-cached-thread-pool`, which is the global default.

`start!`, `suspend!`, `resume!`, `stop!` and `force-stop!` change the state of
the daemon. Request is ignored if it is not possible to change the state, e.g.
a `resume!` request will be ignored for a STOPPED daemon. A freshly created
(READY) or STOPPED daemon must be started (using `start!`) - see table below to
understand the state transitions with respective commands.

                    start!     suspend!     resume!     stop!     force-stop!
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    READY           RUNNING       ---         ---        ---           ---   
    
    RUNNING           ---      SUSPENDED      ---      STOPPED    FORCE_STOPPED
    
    SUSPENDED         ---         ---       RUNNING    STOPPED    FORCE_STOPPED
    
    STOPPED         RUNNING       ---         ---        ---           ---   
    
    FORCE_STOPPED     ---         ---         ---        ---           ---   


Note: FORCE_STOPPED gets automatically transitioned to STOPPED state on effect.


## License

Copyright (C) 2011 Shantanu Kumar
([kumar.shantanu@gmail.com](mailto:kumar.shantanu@gmail.com) and
[@kumarshantanu](http://twitter.com/#!/kumarshantanu))

Distributed under the Eclipse Public License, the same as Clojure.
