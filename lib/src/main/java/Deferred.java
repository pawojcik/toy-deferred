import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

class Deferred<T> {

    private volatile State state;
    private volatile T value;
    private volatile Throwable error;

    private final List<Continuation<T, ?>> continuations;
    private final List<Consumer<Throwable>> rejectedCallbacks;

    Deferred() {
        this.state = State.PENDING;
        this.continuations = new CopyOnWriteArrayList<>();
        this.rejectedCallbacks = new CopyOnWriteArrayList<>();
    }

    public <R> Deferred<R> map(Function<T, R> fn) {
        var continuation = new MapContinuation<>(fn);

        synchronized (this) {
            if (state == State.PENDING) {
                continuations.add(continuation);
                return continuation.promise();
            }
        }

        trigger(continuation);
        return continuation.promise();
    }

    public Deferred<T> whenRejected(Consumer<Throwable> fn) {
        synchronized (this) {
            if (state == State.PENDING) {
                rejectedCallbacks.add(fn);
                return this;
            } 
        }
        
        if (state == State.REJECTED) {
            fn.accept(error);
        }

        return this;
    }

    public <R> Deferred<R> flatMap(Function<T, Deferred<R>> fn) {
        var continuation = new FlatMapContinuation<>(fn);

        synchronized (this) {
            if (state == State.PENDING) {
                continuations.add(continuation);
                return continuation.promise();
            }
        }
        
        trigger(continuation);
        return continuation.promise();
    }

    /**
     * Returns the resolved value of the deferred. 
     * Blocks until the deferred is resolved or rejected.
     * 
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws Rejected if the deferred is rejected
     */
    public synchronized T get() throws InterruptedException, Rejected {
        while (state == State.PENDING) {
            this.wait();
        }
            
        if (state == State.REJECTED) {
            throw new Rejected(error);
        }
        return value; 
    }

    void resolve(T value) {
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            this.value = value;
            this.state = State.RESOLVED;
            
            this.notifyAll(); // Wake up any waiting threads
        }

        // only one thread should go this far
        for (Continuation<T, ?> callback : continuations) {
            trigger(callback);
        }
        continuations.clear();
        rejectedCallbacks.clear();
    }

    void reject(Throwable error) {
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            this.error = error;
            this.state = State.REJECTED;
            this.notifyAll(); // Wake up any waiting threads
        }
        
        // only one thread should go this far
        for (Continuation<T, ?> callback : continuations) {
            trigger(callback);
        }
        continuations.clear();

        for (Consumer<Throwable> fn : rejectedCallbacks) {
            fn.accept(error);
        }
        rejectedCallbacks.clear();
    }

    private void trigger(Continuation<T, ?> continuation) {
        if (state == State.RESOLVED) {
            continuation.resolveWith(value);
        } else if (state == State.REJECTED) {
            continuation.rejectWith(error);
        }
    }

    static enum State {
        PENDING,
        RESOLVED,
        REJECTED
    }

}