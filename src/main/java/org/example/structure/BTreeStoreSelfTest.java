package org.example.structure;

import org.example.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

/**
 * Самопроверка B-дерева без внешних библиотек (запуск: main).
 * Случайные операции сверяются с TreeMap (используется ТОЛЬКО здесь, как эталон),
 * после операций проверяются инварианты дерева.
 */
public class BTreeStoreSelfTest {

    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        randomAgainstReference();
        regressionRemovingMissingKeys();
        duplicatesAreRejected();
        genericKeys();
        visitorsAndStats();
        concurrentSafeStore();
        System.out.println("ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ (" + checks + " проверок)");
    }

    private static Product p(int article) {
        return new Product(article, "n", "c", 1, 1);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void valid(BTreeStore<Integer, Product> tree, String where) {
        String error = tree.validateStructure();
        check(error == null, where + ": структура нарушена — " + error);
    }

    /** 30 сидов: случайные add/remove/find (в том числе удаление отсутствующих ключей) против TreeMap. */
    private static void randomAgainstReference() {
        for (int seed = 0; seed < 30; seed++) {
            Random rnd = new Random(seed);
            BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
            TreeMap<Integer, Product> ref = new TreeMap<>();
            int range = seed % 3 == 0 ? 50 : seed % 3 == 1 ? 2_000 : 100_000;

            for (int step = 0; step < 20_000; step++) {
                int key = rnd.nextInt(range) + 1;
                int op = rnd.nextInt(10);
                if (op < 5) {
                    if (!ref.containsKey(key)) {
                        tree.add(p(key));
                        ref.put(key, p(key));
                    }
                } else if (op < 9) {
                    check(tree.removeByKey(key) == (ref.remove(key) != null), "remove seed=" + seed + " key=" + key);
                } else {
                    check((tree.findByKey(key) != null) == ref.containsKey(key), "find seed=" + seed);
                }
                if (step % 97 == 0) valid(tree, "seed=" + seed + " step=" + step);
            }
            valid(tree, "seed=" + seed);
            check(tree.size() == ref.size(), "size");

            for (int q = 0; q < 200; q++) {
                int a = rnd.nextInt(range) + 1;
                int b = a + rnd.nextInt(range / 4 + 1);
                List<Integer> got = new ArrayList<>();
                tree.forEachInRange(a, b, x -> got.add(x.getArticle()));
                check(got.equals(new ArrayList<>(ref.subMap(a, true, b, true).keySet())),
                        "range [" + a + "," + b + "] seed=" + seed);
            }

            List<Integer> all = new ArrayList<>();
            tree.forEach(x -> all.add(x.getArticle()));
            check(all.equals(new ArrayList<>(ref.keySet())), "forEach порядок/состав");

            for (Map.Entry<Integer, Product> e : new ArrayList<>(ref.entrySet())) {
                check(tree.removeByKey(e.getKey()), "drain");
            }
            valid(tree, "после полного удаления");
            check(tree.size() == 0, "пусто после удаления всего");
        }
    }

    /** Регрессия: удаление несуществующего ключа раньше оставляло внутренний корень без ключей. */
    private static void regressionRemovingMissingKeys() {
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        int[] keys = {5, 3, 1, 8, 2, 7, 4, 6};
        for (int k : keys) tree.add(p(k));
        // Многократно удаляем отсутствующие ключи и сразу проверяем, что существующие по-прежнему удаляются
        for (int missing = 100; missing < 140; missing++) {
            check(!tree.removeByKey(missing), "отсутствующий ключ не должен удаляться");
            valid(tree, "после удаления отсутствующего " + missing);
        }
        for (int k : keys) {
            check(tree.removeByKey(k), "существующий ключ " + k + " должен удаляться");
            check(!tree.removeByKey(k + 1000), "отсутствующий ключ");
            valid(tree, "после удаления " + k);
        }
        check(tree.size() == 0, "дерево пусто");
    }

    private static void duplicatesAreRejected() {
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        tree.add(p(5));
        boolean thrown = false;
        try {
            tree.add(p(5));
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        check(thrown, "дубликат должен вызывать IllegalArgumentException");
        check(tree.size() == 1, "размер не меняется при дубликате");
        valid(tree, "после попытки добавить дубликат");

        // Дубликат при полном корне не должен ломать структуру (раньше корень делился до проверки)
        BTreeStore<Integer, Product> full = new BTreeStore<>(Product::getArticle);
        for (int k = 1; k <= 3; k++) full.add(p(k));
        try { full.add(p(2)); } catch (IllegalArgumentException ignored) { }
        check(full.getRoot().isLeaf(), "корень не должен делиться при отклонённом дубликате");
    }

    /** Структура действительно обобщённая: String-ключи, свой экстрактор. */
    private static void genericKeys() {
        BTreeStore<String, String> tree = new BTreeStore<>(s -> s);
        String[] words = {"pear", "apple", "fig", "kiwi", "plum", "date", "lime", "mango", "nut"};
        for (String w : words) tree.add(w);
        check("fig".equals(tree.findByKey("fig")), "find по строковому ключу");
        check(tree.findByKey("zzz") == null, "отсутствующий строковый ключ");
        List<String> range = new ArrayList<>();
        tree.forEachInRange("b", "l", range::add);
        check(range.equals(List.of("date", "fig", "kiwi")), "диапазон строк: " + range);
        check(tree.validateStructure() == null, "структура String-дерева");
    }

    private static void visitorsAndStats() {
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        check(tree.getStats().getTotalNodes() == 0, "пустое дерево: 0 узлов");
        for (int i = 1; i <= 1000; i++) tree.add(p(i));

        TreeStats stats = tree.getStats();
        int[] visited = {0};
        check(tree.findByKey(777, n -> visited[0]++) != null, "find 777");
        check(visited[0] >= 1 && visited[0] <= stats.getHeight(), "путь поиска не длиннее высоты");

        visited[0] = 0;
        tree.add(p(5000), n -> visited[0]++);
        check(visited[0] >= 1 && visited[0] <= tree.getStats().getHeight(), "путь вставки не длиннее высоты");

        int sum = 0;
        for (int c : tree.getStats().getKeysHistogram()) sum += c;
        check(sum == tree.getStats().getTotalNodes(), "гистограмма даёт общее число узлов");
        check(tree.getStats().getTotalKeys() == tree.size(), "totalKeys == size");
    }

    private static void concurrentSafeStore() throws InterruptedException {
        int threads = 8, per = 20_000;
        SynchronizedBTreeStore<Integer, Product> store = new SynchronizedBTreeStore<>(Product::getArticle);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread th = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < per; i++) store.add(p(i * threads + id));
            });
            list.add(th);
            th.start();
        }
        start.countDown();
        for (Thread th : list) th.join();
        int[] n = {0};
        store.forEach(x -> n[0]++);
        check(store.size() == threads * per && n[0] == threads * per, "synchronized: ничего не потеряно");
        check(store.validateStructure() == null, "synchronized: структура цела");
    }
}
