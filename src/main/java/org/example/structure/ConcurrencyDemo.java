package org.example.structure;

import org.example.model.Product;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Демонстрация гонки на B-дереве без защиты и с защитой.
 *
 * Все потоки одновременно вставляют в ОДНО дерево; ключи разных потоков перемешаны
 * (поток t пишет t, t+THREADS, t+2*THREADS, ...), так что они постоянно лезут в одни и те же узлы.
 * Потерю данных проверяем не по счётчику size(), а по реальному содержимому узлов,
 * плюс валидируем структуру и считаем потоки, упавшие с исключением.
 *
 * Гонка недетерминирована: без защиты «повезти» может, поэтому запусков несколько.
 */
public class ConcurrencyDemo {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 50_000;
    private static final int ATTEMPTS = 3;
    private static final long TIMEOUT_SECONDS = 60;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Демонстрация конкурентной записи в B-дерево ===");
        System.out.printf("Потоков: %d, вставок на поток: %d, всего: %d%n%n",
                THREADS, PER_THREAD, THREADS * PER_THREAD);

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            BTreeStore<Integer, Product> unsafe = new BTreeStore<>(Product::getArticle);
            Outcome o1 = runWriters(unsafe);
            report("Без защиты ", attempt, o1, unsafe.size(),
                    countReal(unsafe::forEach), unsafe.validateStructure());

            SynchronizedBTreeStore<Integer, Product> safe = new SynchronizedBTreeStore<>(Product::getArticle);
            Outcome o2 = runWriters(safe);
            report("synchronized", attempt, o2, safe.size(),
                    countReal(safe::forEach), safe.validateStructure());
            System.out.println();
        }
    }

    /** Итог одного прогона. */
    private record Outcome(int crashedThreads, boolean timedOut, long millis) { }

    private static Outcome runWriters(EntityStore<Integer, Product> store) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(THREADS);
        AtomicInteger crashed = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            final int id = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        int article = i * THREADS + id;
                        store.add(new Product(article, "Товар", "Кат", 1, 1));
                    }
                } catch (Throwable e) {          // в гонке возможны любые исключения
                    crashed.incrementAndGet();
                } finally {
                    end.countDown();
                }
            });
            thread.setDaemon(true);              // зависший поток не должен держать JVM
            thread.start();
        }

        long t0 = System.nanoTime();
        start.countDown();                       // СТАРТ — все потоки одновременно
        boolean finished = end.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long millis = (System.nanoTime() - t0) / 1_000_000;
        return new Outcome(crashed.get(), !finished, millis);
    }

    /** Считает элементы обходом дерева; -1, если обход сломался на повреждённой структуре. */
    private static int countReal(Consumer<Consumer<Product>> iteration) {
        int[] n = {0};
        try {
            iteration.accept(p -> n[0]++);
            return n[0];
        } catch (Throwable e) {
            return -1;
        }
    }

    private static void report(String title, int attempt, Outcome o, int size, int real, String structureError) {
        int expected = THREADS * PER_THREAD;
        System.out.printf("[%d] %s | %5d мс | ожидалось %d | size() = %d | реально в узлах = %d | упало потоков: %d%s%n",
                attempt, title, o.millis(), expected, size, real, o.crashedThreads(),
                o.timedOut() ? " | ТАЙМАУТ" : "");
        boolean ok = size == expected && real == expected && o.crashedThreads() == 0 && structureError == null;
        System.out.println("      итог: " + (ok ? "всё корректно" : "ДАННЫЕ ПОВРЕЖДЕНЫ")
                + (structureError != null ? " — " + structureError : ""));
    }
}
