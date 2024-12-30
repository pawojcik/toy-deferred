
import java.util.function.Function;

// not thread safe
class FlatMapContinuation<T, R> implements Continuation<T, R> {
    private final Function<T, Deferred<R>> fn;
    private final Deferred<R> promise;

    public FlatMapContinuation(Function<T, Deferred<R>> fn) {
        this.fn = fn;
        this.promise = new Deferred<>();
    }

    @Override
    public Deferred<R> promise() {
        return promise;
    }

    @Override
    public void resolveWith(T value) {
        try {
            Deferred<R> resultPromise = fn.apply(value);
            resultPromise.map(rval -> {
                promise.resolve(rval);
                // the result is not used here
                return null;
            }).whenRejected(error -> {
                promise.reject(error);
            });
        } catch (Throwable error) {
            promise.reject(error);
        }
    }

    @Override
    public void rejectWith(Throwable error) {
        promise.reject(error);
    }
}