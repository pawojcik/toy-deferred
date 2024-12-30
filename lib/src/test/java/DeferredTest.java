import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class DeferredTest {

    @Test
    void should_resolve_to_given_value() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();

        // when
        d.resolve(1);

        // then
        assertEquals(1, d.get());
    }

    @Test
    void should_only_resolve_once() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();

        // when
        d.resolve(1);
        d.resolve(2);

        // then
        assertEquals(1, d.get());
    }

    @Test
    void should_execute_rejected_callback() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestConsumer<Throwable> fn = new TestConsumer<>(t -> {
            assertEquals("test", t.getMessage());
        });

        // when
        d.whenRejected(fn);
        d.reject(new RuntimeException("test"));

        // then
        assertTrue(fn.wasExecutedOnce());
    }
    
    @Test
    void should_only_reject_once() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestConsumer<Throwable> fn = new TestConsumer<>(t -> {
            assertEquals("test", t.getMessage());
        });

        // when
        d.whenRejected(fn);
        d.reject(new RuntimeException("test"));
        d.reject(new RuntimeException("test2"));
        // then
        assertTrue(fn.wasExecutedOnce());
    }

    @Test
    void should_execute_reject_callback_if_its_added_after_reject() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestConsumer<Throwable> fn = new TestConsumer<>(t -> {
            assertEquals("test", t.getMessage());
        });

        // when
        d.reject(new RuntimeException("test"));
        d.whenRejected(fn);
        // then
        assertTrue(fn.wasExecutedOnce());
    }

    @Test
    void should_map_resolved_value() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestFunction<Integer, Integer> fn = new TestFunction<>(x -> x + 1);

        // when
        var p = d.map(fn);
        d.resolve(1);

        // then
        assertEquals(2, p.get());
        assertTrue(fn.wasExecutedOnce());
    }

    @Test
    void should_map_after_resolve() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestFunction<Integer, Integer> fn = new TestFunction<>(x -> x + 1);

        // when
        d.resolve(1);
        var p = d.map(fn);

        // then
        assertEquals(2, p.get());
        assertTrue(fn.wasExecutedOnce());
    }

    @Test
    void should_not_map_and_propagate_rejection() {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestFunction<Integer, Integer> fn = new TestFunction<>(x -> x + 1);

        // when
        var p = d.map(fn);
        d.reject(new RuntimeException("test"));

        // then
        assertThrows(Rejected.class, () -> p.get());
        assertFalse(fn.wasExecutedOnce());
    }

    @Test
    void should_not_map_and_propagate_rejection_after_rejected() {
        // given
        Deferred<Integer> d = new Deferred<>();
        TestFunction<Integer, Integer> fn = new TestFunction<>(x -> x + 1);

        // when
        d.reject(new RuntimeException("test"));
        var p = d.map(fn);

        // then
        assertThrows(Rejected.class, () -> p.get());
        assertFalse(fn.wasExecutedOnce());
    }

    @Test
    void should_resolve_to_inner_promise_value() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        Deferred<Integer> inner = new Deferred<>();

        // when
        var p = d.flatMap(x -> inner);
        d.resolve(1);
        inner.resolve(2);

        // then
        assertEquals(2, p.get());
    }

    @Test
    void should_resolve_to_inner_promise_value_if_inner_is_first() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        Deferred<Integer> inner = new Deferred<>();

        // when
        inner.resolve(2);
        var p = d.flatMap(x -> inner);
        d.resolve(1);

        // then
        assertEquals(2, p.get());
    }

    @Test
    void should_not_resolve_to_inner_promise_value_if_outer_promise_is_rejected() {
        // given
        Deferred<Integer> d = new Deferred<>();
        Deferred<Integer> inner = new Deferred<>();
        TestFunction<Integer, Deferred<Integer>> fn = new TestFunction<>(x -> inner);
    
        // when
        var p = d.flatMap(fn);
        d.reject(new RuntimeException("test"));
    
        // then
        assertThrows(Rejected.class, () -> p.get());
        assertFalse(fn.wasExecutedOnce());
    }

    @Test
    void should_reject_if_inner_promise_is_rejected() throws InterruptedException, Rejected {
        // given
        Deferred<Integer> d = new Deferred<>();
        Deferred<Integer> inner = new Deferred<>();
        TestFunction<Integer, Deferred<Integer>> fn = new TestFunction<>(x -> inner);

        // when
        var p = d.flatMap(fn);
        d.resolve(1);
        inner.reject(new RuntimeException("test"));

        // then
        assertThrows(Rejected.class, () -> p.get());
        assertTrue(fn.wasExecutedOnce());
    }

    static class TestFunction<A, R> implements Function<A, R> {

        final Function<A, R> fn;
        int executionCount = 0;

        TestFunction(Function<A, R> fn) {
            this.fn = fn;
        }

        @Override
        public R apply(A a) {
            executionCount++;
            return fn.apply(a);
        }

        boolean wasExecutedOnce() {
            return executionCount == 1;
        }
    }

    static class TestConsumer<A> implements Consumer<A> {

        final Consumer<A> consumer;
        int executionCount = 0;

        TestConsumer(Consumer<A> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void accept(A a) {
            executionCount++;
            consumer.accept(a);
        }

        boolean wasExecutedOnce() {
            return executionCount == 1;
        }
    }
}
