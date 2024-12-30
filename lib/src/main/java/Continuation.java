interface Continuation<T, R> {
    Deferred<R> promise();
    void resolveWith(T value);
    void rejectWith(Throwable error);
}