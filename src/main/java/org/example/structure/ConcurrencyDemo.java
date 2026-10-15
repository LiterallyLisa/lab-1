package org.example.structure;

import org.example.model.Product;

import java.util.concurrent.CountDownLatch;

/**
 * Демонстрация гонки на B-дереве без защиты и с защитой.
 */
public class ConcurrencyDemo {

    private static final int THREADS = 5;
    private static final int PER_THREAD = 1000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Демонстрация concurrency ===");

        // 1. Без защиты
        demoUnsafe();

        // 2. С защитой (synchronized)
        demoSafe();
    }

    // ============================================================
    //            БЕЗ ЗАЩИТЫ (гонка)
    // ============================================================

    private static void demoUnsafe() throws InterruptedException {
        System.out.println("\n--- Без защиты (ожидаем гонку) ---");
        BTreeStore<Integer, Product> tree = new BTreeStore<>();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();   // ждём сигнала
                    for (int i = 0; i < PER_THREAD; i++) {
                        int article = threadId * PER_THREAD + i;
                        tree.add(new Product(article, "Товар", "Кат", 1, 1));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();   // СТАРТ!
        endLatch.await();          // ждём все потоки

        int expected = THREADS * PER_THREAD;
        System.out.println("Ожидалось: " + expected);
        System.out.println("Получено:  " + tree.size());
        System.out.println("Потеряно:  " + (expected - tree.size()));
    }

    // ============================================================
    //            С ЗАЩИТОЙ (synchronized)
    // ============================================================

    private static void demoSafe() throws InterruptedException {
        System.out.println("\n--- С защитой (synchronized) ---");
        SynchronizedBTreeStore tree = new SynchronizedBTreeStore();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        int article = threadId * PER_THREAD + i;
                        tree.add(new Product(article, "Товар", "Кат", 1, 1));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await();

        int expected = THREADS * PER_THREAD;
        System.out.println("Ожидалось: " + expected);
        System.out.println("Получено:  " + tree.size());
        System.out.println("Потеряно:  " + (expected - tree.size()));
    }
}