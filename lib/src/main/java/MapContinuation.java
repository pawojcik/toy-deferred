
import java.util.function.Function;

// not thread safe
class MapContinuation<T, R> implements Continuation<T, R> {

    private final Deferred<R> promise;
    private final Function<T, R> fn;

    MapContinuation(Function<T, R> fn) {
        this.promise = new Deferred<>();
        this.fn = fn;
    }

    @Override
    public Deferred<R> promise() {
        return promise;
    }

    @Override
    public void resolveWith(T value) {
        try {
          promise.resolve(fn.apply(value));
        } catch (Throwable error) {
          promise.reject(error);
        }
    }

    @Override
    public void rejectWith(Throwable error) {
        promise.reject(error);
    }
}
