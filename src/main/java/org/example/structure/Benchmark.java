package org.example.structure;

import org.example.model.Product;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Замер производительности B-дерева в сравнении с TreeMap.
 * Только для эксперимента — в основном коде TreeMap не используется.
 *
 * Методика:
 *  - данные создаются заранее, создание объектов не попадает в замер;
 *  - несколько прогревочных раундов, затем измеряемые; в отчёт идёт медиана по раундам;
 *  - время делится на число операций (нс/оп), а не округляется до миллисекунд;
 *  - результаты операций «потребляются» (sink), чтобы JIT не выбросил вызовы как мёртвый код;
 *  - порядок вставки и порядок поиска/удаления — и возрастающий, и случайный;
 *  - измеряются все операции: вставка, поиск, удаление, диапазонный скан, статистика.
 */
public class Benchmark {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;
    private static final int[] SIZES = {10_000, 100_000};
    private static final int RANGE_QUERIES = 1_000;
    private static final int FIRST_ARTICLE = 1000;

    private static final int INSERT = 0, FIND = 1, REMOVE = 2, RANGE = 3, STATS = 4, OPS = 5;
    private static final String[] OP_NAMES = {
            "Вставка", "Поиск", "Удаление", "Диапазон (1% ключей)", "Статистика (обход)"};
    private static final String[] OP_UNITS = {"нс/оп", "нс/оп", "нс/оп", "нс/запрос", "мкс"};

    /** Накопитель результатов: не даёт JIT выбросить вычисления. */
    private static long sink;

    public static void main(String[] args) {
        System.out.println("Сравнение BTreeStore и TreeMap (медиана из " + MEASURED_ROUNDS
                + " раундов после " + WARMUP_ROUNDS + " прогревочных)");
        for (int size : SIZES) {
            for (boolean ascending : new boolean[]{true, false}) {
                compare(size, ascending);
            }
        }
        System.out.println("\n(контрольная сумма: " + sink + ")");
    }

    public static void compare(int n, boolean ascendingInsert) {
        System.out.printf("%n=== %d элементов, вставка в %s порядке ===%n",
                n, ascendingInsert ? "ВОЗРАСТАЮЩЕМ" : "СЛУЧАЙНОМ");

        Random random = new Random(42);
        Product[] items = new Product[n];
        for (int i = 0; i < n; i++) {
            items[i] = new Product(FIRST_ARTICLE + i, "Товар", "Кат", 1, i % 100);
        }
        if (!ascendingInsert) {
            shuffle(items, random);
        }
        int[] findOrder = shuffledKeys(n, random);
        int[] removeOrder = shuffledKeys(n, random);
        int[][] ranges = ranges(n, random);

        double[][] bt = new double[MEASURED_ROUNDS][];
        double[][] tm = new double[MEASURED_ROUNDS][];
        for (int r = 0; r < WARMUP_ROUNDS + MEASURED_ROUNDS; r++) {
            double[] b = roundBTree(items, findOrder, removeOrder, ranges);
            double[] t = roundTreeMap(items, findOrder, removeOrder, ranges);
            if (r >= WARMUP_ROUNDS) {
                bt[r - WARMUP_ROUNDS] = b;
                tm[r - WARMUP_ROUNDS] = t;
            }
        }

        System.out.printf("%-26s %12s %12s %10s   %s%n", "Операция", "BTreeStore", "TreeMap", "B/Tree", "ед.");
        for (int op = 0; op < OPS; op++) {
            double b = median(bt, op);
            double t = median(tm, op);
            String tStr = Double.isNaN(t) ? "—" : String.format(Locale.ROOT, "%.1f", t);
            String ratio = Double.isNaN(t) ? "—" : String.format(Locale.ROOT, "%.2f", b / t);
            System.out.printf("%-26s %12s %12s %10s   %s%n",
                    OP_NAMES[op], String.format(Locale.ROOT, "%.1f", b), tStr, ratio, OP_UNITS[op]);
        }

        // Структура получившегося B-дерева — пригодится для отчёта
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        for (Product p : items) tree.add(p);
        System.out.println("Структура: " + tree.getStats());
    }

    // ============================================================
    //                    ОДИН РАУНД
    // ============================================================

    private static double[] roundBTree(Product[] items, int[] findOrder, int[] removeOrder, int[][] ranges) {
        int n = items.length;
        double[] res = new double[OPS];
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);

        long t0 = System.nanoTime();
        for (Product p : items) tree.add(p);
        res[INSERT] = perOp(t0, n);

        long acc = 0;
        t0 = System.nanoTime();
        for (int key : findOrder) acc += tree.findByKey(key).getStock();
        res[FIND] = perOp(t0, n);

        long[] counter = {0};
        Consumer<Product> consumer = p -> counter[0] += p.getStock();
        t0 = System.nanoTime();
        for (int[] q : ranges) tree.forEachInRange(q[0], q[1], consumer);
        res[RANGE] = perOp(t0, ranges.length);
        acc += counter[0];

        t0 = System.nanoTime();
        TreeStats stats = tree.getStats();
        res[STATS] = (System.nanoTime() - t0) / 1_000.0;
        acc += stats.getTotalNodes();

        t0 = System.nanoTime();
        for (int key : removeOrder) tree.removeByKey(key);
        res[REMOVE] = perOp(t0, n);
        acc += tree.size();

        sink += acc;
        return res;
    }

    private static double[] roundTreeMap(Product[] items, int[] findOrder, int[] removeOrder, int[][] ranges) {
        int n = items.length;
        double[] res = new double[OPS];
        TreeMap<Integer, Product> map = new TreeMap<>();

        long t0 = System.nanoTime();
        for (Product p : items) map.put(p.getArticle(), p);
        res[INSERT] = perOp(t0, n);

        long acc = 0;
        t0 = System.nanoTime();
        for (int key : findOrder) acc += map.get(key).getStock();
        res[FIND] = perOp(t0, n);

        t0 = System.nanoTime();
        for (int[] q : ranges) {
            for (Product p : map.subMap(q[0], true, q[1], true).values()) acc += p.getStock();
        }
        res[RANGE] = perOp(t0, ranges.length);

        res[STATS] = Double.NaN;     // у TreeMap нет аналога

        t0 = System.nanoTime();
        for (int key : removeOrder) map.remove(key);
        res[REMOVE] = perOp(t0, n);
        acc += map.size();

        sink += acc;
        return res;
    }

    // ============================================================
    //                    ВСПОМОГАТЕЛЬНЫЕ
    // ============================================================

    private static double perOp(long startNanos, int operations) {
        return (double) (System.nanoTime() - startNanos) / operations;
    }

    private static double median(double[][] rounds, int op) {
        double[] values = new double[rounds.length];
        for (int i = 0; i < rounds.length; i++) values[i] = rounds[i][op];
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static void shuffle(Product[] a, Random random) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Product tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    private static int[] shuffledKeys(int n, Random random) {
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) keys[i] = FIRST_ARTICLE + i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = keys[i];
            keys[i] = keys[j];
            keys[j] = tmp;
        }
        return keys;
    }

    /** Окна шириной 1% от диапазона ключей со случайным началом. */
    private static int[][] ranges(int n, Random random) {
        int width = Math.max(1, n / 100);
        int[][] ranges = new int[RANGE_QUERIES][2];
        for (int i = 0; i < RANGE_QUERIES; i++) {
            int from = FIRST_ARTICLE + random.nextInt(n - width + 1);
            ranges[i][0] = from;
            ranges[i][1] = from + width - 1;
        }
        return ranges;
    }
}
