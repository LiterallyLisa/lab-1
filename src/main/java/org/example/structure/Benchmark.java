package org.example.structure;

import org.example.model.Product;

import java.util.TreeMap;

/**
 * Замер производительности B-дерева и TreeMap.
 * Только для эксперимента — в основном коде TreeMap не используется.
 */
public class Benchmark {

    /** Количество прогонов для усреднения. */
    private static final int RUNS = 3;

    /**
     * Сравнивает BTreeStore и TreeMap на заданном количестве элементов.
     */
    public static void compare(int count) {
        System.out.println("\n=== Сравнение: " + count + " элементов ===");

        // --- Прогреваем JIT ---
        for (int i = 0; i < 3; i++) {
            warmUp(count);
        }

        // --- B-дерево ---
        long btreeInsert = timeBTreeInsert(count);
        long btreeFind   = timeBTreeFind(count);
        long btreeRemove = timeBTreeRemove(count);

        // --- TreeMap ---
        long mapInsert = timeMapInsert(count);
        long mapFind   = timeMapFind(count);
        long mapRemove = timeMapRemove(count);

        // --- Вывод ---
        System.out.printf("%-15s %12s %12s%n", "Операция", "BTreeStore,мс", "TreeMap,мс");
        System.out.printf("%-15s %12d %12d%n", "Вставка",  btreeInsert, mapInsert);
        System.out.printf("%-15s %12d %12d%n", "Поиск",    btreeFind,   mapFind);
        System.out.printf("%-15s %12d %12d%n", "Удаление", btreeRemove, mapRemove);
    }

    /** Прогрев — чтобы JIT оптимизировал код. */
    private static void warmUp(int count) {
        BTreeStore<Integer, Product> tree = new BTreeStore<>();
        TreeMap<Integer, Product> map = new TreeMap<>();
        for (int i = 0; i < Math.min(count, 1000); i++) {
            tree.add(new Product(1000 + i, "T", "C", 1, 1));
            map.put(1000 + i, new Product(1000 + i, "T", "C", 1, 1));
        }
    }

    // ============================================================
    //                    B-ДЕРЕВО
    // ============================================================

    private static long timeBTreeInsert(int count) {
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            BTreeStore<Integer, Product> tree = new BTreeStore<>();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                tree.add(new Product(1000 + i, "Товар", "Кат", 1, 1));
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;   // в миллисекундах
    }

    private static long timeBTreeFind(int count) {
        BTreeStore<Integer, Product> tree = new BTreeStore<>();
        for (int i = 0; i < count; i++) {
            tree.add(new Product(1000 + i, "Товар", "Кат", 1, 1));
        }
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                tree.findByKey(1000 + i);
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;
    }

    private static long timeBTreeRemove(int count) {
        BTreeStore<Integer, Product> tree = new BTreeStore<>();
        for (int i = 0; i < count; i++) {
            tree.add(new Product(1000 + i, "Товар", "Кат", 1, 1));
        }
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            BTreeStore<Integer, Product> copy = new BTreeStore<>();
            for (int i = 0; i < count; i++) {
                copy.add(new Product(1000 + i, "Товар", "Кат", 1, 1));
            }
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                copy.removeByKey(1000 + i);
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;
    }

    // ============================================================
    //                    TreeMap
    // ============================================================

    private static long timeMapInsert(int count) {
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            TreeMap<Integer, Product> map = new TreeMap<>();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                map.put(1000 + i, new Product(1000 + i, "Товар", "Кат", 1, 1));
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;
    }

    private static long timeMapFind(int count) {
        TreeMap<Integer, Product> map = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            map.put(1000 + i, new Product(1000 + i, "Товар", "Кат", 1, 1));
        }
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                map.get(1000 + i);
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;
    }

    private static long timeMapRemove(int count) {
        long total = 0;
        for (int run = 0; run < RUNS; run++) {
            TreeMap<Integer, Product> map = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                map.put(1000 + i, new Product(1000 + i, "Товар", "Кат", 1, 1));
            }
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                map.remove(1000 + i);
            }
            total += System.nanoTime() - start;
        }
        return total / RUNS / 1_000_000;
    }

    /** Точка входа для запуска сравнения. */
    public static void main(String[] args) {
        System.out.println("Запуск сравнения BTreeStore и TreeMap...");
        compare(1000);
        compare(10000);
        compare(100000);
    }
}