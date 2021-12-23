package main.codec;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MyExecutorServiceBuilder {

    private final String name;
    private final int threads;

    public MyExecutorServiceBuilder(int threads, String name) {
        this.name = name;
        this.threads = threads;
    }

    public ExecutorService build() {
        return Executors.newFixedThreadPool(threads, new MyThreadFactory());
    }

    private class MyThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, name + "_" + counter.incrementAndGet());
        }
    }
}
