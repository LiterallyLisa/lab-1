// Пакет structure
package org.example.structure;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * B-дерево порядка 4 — хранилище пар ключ-значение.
 * Реализует интерфейс EntityStore<K, T>.
 *
 * Порядок 4:
 *  - до 3 ключей в узле;
 *  - до 4 детей в узле;
 *  - все листья на одном уровне.
 *
 * Ключ элемента вычисляется переданной в конструктор функцией (например, Product::getArticle),
 * поэтому структура по-настоящему обобщённая и ничего не знает о конкретной модели.
 * Ключи уникальны. Класс не потокобезопасен — см. SynchronizedBTreeStore.
 */
public class BTreeStore<K extends Comparable<K>, T> implements EntityStore<K, T> {

    /** Порядок B-дерева. */
    public static final int ORDER = BTreeNode.ORDER;

    /** Максимум ключей в узле (order - 1). */
    public static final int MAX_KEYS = BTreeNode.MAX_KEYS;

    /** Максимум детей в узле (order). */
    public static final int MAX_CHILDREN = BTreeNode.MAX_CHILDREN;

    /** Минимум ключей в узле (кроме корня). */
    public static final int MIN_KEYS = (ORDER + 1) / 2 - 1;

    /** Защита validateStructure от зацикленных (повреждённых) деревьев. */
    private static final int MAX_DEPTH_GUARD = 64;

    /** Как получить ключ из элемента. */
    private final Function<? super T, ? extends K> keyExtractor;

    /** Корень дерева. */
    private BTreeNode<K, T> root;

    /** Количество элементов в дереве. */
    private int size;

    /**
     * Конструктор: создаём пустое дерево.
     * @param keyExtractor функция, достающая ключ из элемента (например, Product::getArticle)
     */
    public BTreeStore(Function<? super T, ? extends K> keyExtractor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
        this.root = new BTreeNode<>(true);
        this.size = 0;
    }

    // ============================================================
    //                     ПУБЛИЧНЫЕ МЕТОДЫ
    // ============================================================

    /**
     * Добавить значение. Ключ берётся из самого объекта.
     * @throws IllegalArgumentException если элемент с таким ключом уже есть
     */
    @Override
    public void add(T entity) {
        add(entity, null);
    }

    /**
     * Добавить значение и сообщить visitor-у о каждом узле, через который прошла вставка
     * (для подсветки в визуализации).
     * @throws IllegalArgumentException если элемент с таким ключом уже есть
     */
    public void add(T entity, Consumer<? super BTreeNode<K, T>> visitor) {
        Objects.requireNonNull(entity, "entity");
        K key = Objects.requireNonNull(keyExtractor.apply(entity), "key");

        // Проверка ДО любых изменений структуры: при дубликате дерево остаётся нетронутым
        if (findByKey(key) != null) {
            throw new IllegalArgumentException("Ключ уже существует: " + key);
        }

        if (root.isFull()) {
            BTreeNode<K, T> newRoot = new BTreeNode<>(false);
            newRoot.setChild(0, root);
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, key, entity, visitor);
        size++;
    }

    /**
     * Найти значение по ключу.
     */
    @Override
    public T findByKey(K key) {
        return findByKey(key, null);
    }

    /**
     * Найти значение по ключу и сообщить visitor-у о каждом посещённом узле
     * (от корня вниз) — для подсветки пути поиска.
     */
    public T findByKey(K key, Consumer<? super BTreeNode<K, T>> visitor) {
        BTreeNode<K, T> node = root;
        while (true) {
            if (visitor != null) {
                visitor.accept(node);
            }
            int index = node.findKeyIndex(key);
            if (index < node.getKeyCount() && node.getKey(index).compareTo(key) == 0) {
                return node.getValue(index);
            }
            if (node.isLeaf()) {
                return null;
            }
            node = node.getChild(index);
        }
    }

    /**
     * Удалить значение по ключу.
     */
    @Override
    public boolean removeByKey(K key) {
        if (root.getKeyCount() == 0) {
            return false;
        }
        boolean removed = removeFromNode(root, key);
        if (removed) {
            size--;
        }
        // Слияния узлов при спуске происходят и тогда, когда ключ не найден,
        // поэтому корень нужно сворачивать всегда, а не только при успешном удалении.
        if (root.getKeyCount() == 0 && !root.isLeaf()) {
            root = root.getChild(0);
        }
        return removed;
    }

    /**
     * Количество элементов в дереве.
     */
    @Override
    public int size() {
        return size;
    }

    /** Возвращает корень дерева — для визуализации (узел доступен только для чтения). */
    public BTreeNode<K, T> getRoot() {
        return root;
    }

    // ============================================================
    //                     ВСТАВКА
    // ============================================================

    private void insertNonFull(BTreeNode<K, T> node, K key, T value,
                               Consumer<? super BTreeNode<K, T>> visitor) {
        if (visitor != null) {
            visitor.accept(node);
        }
        int index = node.findKeyIndex(key);
        if (node.isLeaf()) {
            node.insertKey(key, value);
        } else {
            if (node.getChild(index).isFull()) {
                splitChild(node, index);
                int cmp = node.getKey(index).compareTo(key);
                if (cmp < 0) {
                    index++;
                }
            }
            insertNonFull(node.getChild(index), key, value, visitor);
        }
    }

    private void splitChild(BTreeNode<K, T> parent, int childIndex) {
        BTreeNode<K, T> fullChild = parent.getChild(childIndex);
        BTreeNode<K, T> newChild = new BTreeNode<>(fullChild.isLeaf());

        int mid = MAX_KEYS / 2;

        for (int i = mid + 1; i < MAX_KEYS; i++) {
            newChild.insertKey(fullChild.getKey(i), fullChild.getValue(i));
        }
        if (!fullChild.isLeaf()) {
            for (int i = mid + 1; i <= MAX_KEYS; i++) {
                newChild.setChild(i - (mid + 1), fullChild.getChild(i));
                fullChild.setChild(i, null);   // ребёнок теперь принадлежит newChild
            }
        }
        for (int i = parent.getKeyCount(); i > childIndex; i--) {
            parent.setChild(i + 1, parent.getChild(i));
        }
        parent.setChild(childIndex + 1, newChild);
        parent.insertKey(fullChild.getKey(mid), fullChild.getValue(mid));
        fullChild.truncate(mid);
    }

    // ============================================================
    //                     УДАЛЕНИЕ
    // ============================================================

    private boolean removeFromNode(BTreeNode<K, T> node, K key) {
        int index = node.findKeyIndex(key);

        // Случай 1: ключ найден в узле
        if (index < node.getKeyCount() && node.getKey(index).compareTo(key) == 0) {
            if (node.isLeaf()) {
                node.removeKey(index);
                return true;
            } else {
                return removeFromInternalNode(node, index);
            }
        }

        // Случай 2: ключа в узле нет
        if (node.isLeaf()) {
            return false;
        }

        // Упреждающее обеспечение: в ребёнка заходим, только если в нём больше MIN_KEYS ключей
        if (node.getChild(index).getKeyCount() == MIN_KEYS) {
            fillChild(node, index);
        }

        if (index > node.getKeyCount()) {
            return removeFromNode(node.getChild(index - 1), key);
        } else {
            return removeFromNode(node.getChild(index), key);
        }
    }

    private boolean removeFromInternalNode(BTreeNode<K, T> node, int index) {
        K key = node.getKey(index);
        BTreeNode<K, T> leftChild = node.getChild(index);
        BTreeNode<K, T> rightChild = node.getChild(index + 1);

        if (leftChild.getKeyCount() > MIN_KEYS) {
            BTreeNode<K, T> pred = rightmostLeaf(leftChild);
            K predKey = pred.getKey(pred.getKeyCount() - 1);
            T predValue = pred.getValue(pred.getKeyCount() - 1);
            node.setKey(index, predKey);
            node.setValue(index, predValue);
            return removeFromNode(leftChild, predKey);
        } else if (rightChild.getKeyCount() > MIN_KEYS) {
            BTreeNode<K, T> succ = leftmostLeaf(rightChild);
            K succKey = succ.getKey(0);
            T succValue = succ.getValue(0);
            node.setKey(index, succKey);
            node.setValue(index, succValue);
            return removeFromNode(rightChild, succKey);
        } else {
            mergeChildren(node, index);
            return removeFromNode(leftChild, key);
        }
    }

    private BTreeNode<K, T> rightmostLeaf(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(node.getKeyCount());
        }
        return node;
    }

    private BTreeNode<K, T> leftmostLeaf(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(0);
        }
        return node;
    }

    private void fillChild(BTreeNode<K, T> node, int index) {
        if (index > 0 && node.getChild(index - 1).getKeyCount() > MIN_KEYS) {
            borrowFromLeft(node, index);
        } else if (index < node.getKeyCount() && node.getChild(index + 1).getKeyCount() > MIN_KEYS) {
            borrowFromRight(node, index);
        } else {
            if (index < node.getKeyCount()) {
                mergeChildren(node, index);
            } else {
                mergeChildren(node, index - 1);
            }
        }
    }

    private void borrowFromLeft(BTreeNode<K, T> parent, int index) {
        BTreeNode<K, T> child = parent.getChild(index);
        BTreeNode<K, T> leftSibling = parent.getChild(index - 1);

        for (int i = child.getKeyCount(); i > 0; i--) {
            child.setKey(i, child.getKey(i - 1));
            child.setValue(i, child.getValue(i - 1));
        }
        child.setKey(0, parent.getKey(index - 1));
        child.setValue(0, parent.getValue(index - 1));
        child.setKeyCount(child.getKeyCount() + 1);

        parent.setKey(index - 1, leftSibling.getKey(leftSibling.getKeyCount() - 1));
        parent.setValue(index - 1, leftSibling.getValue(leftSibling.getKeyCount() - 1));
        leftSibling.removeKey(leftSibling.getKeyCount() - 1);

        if (!child.isLeaf()) {
            for (int i = child.getKeyCount(); i > 0; i--) {
                child.setChild(i, child.getChild(i - 1));
            }
            child.setChild(0, leftSibling.getChild(leftSibling.getKeyCount() + 1));
            leftSibling.setChild(leftSibling.getKeyCount() + 1, null);
        }
    }

    private void borrowFromRight(BTreeNode<K, T> parent, int index) {
        BTreeNode<K, T> child = parent.getChild(index);
        BTreeNode<K, T> rightSibling = parent.getChild(index + 1);

        child.setKey(child.getKeyCount(), parent.getKey(index));
        child.setValue(child.getKeyCount(), parent.getValue(index));
        child.setKeyCount(child.getKeyCount() + 1);

        parent.setKey(index, rightSibling.getKey(0));
        parent.setValue(index, rightSibling.getValue(0));
        rightSibling.removeKey(0);

        if (!child.isLeaf()) {
            child.setChild(child.getKeyCount(), rightSibling.getChild(0));
            for (int i = 0; i < rightSibling.getKeyCount() + 1; i++) {
                rightSibling.setChild(i, rightSibling.getChild(i + 1));
            }
            rightSibling.setChild(rightSibling.getKeyCount() + 1, null);
        }
    }

    private void mergeChildren(BTreeNode<K, T> parent, int index) {
        BTreeNode<K, T> leftChild = parent.getChild(index);
        BTreeNode<K, T> rightChild = parent.getChild(index + 1);

        // Исходное количество ключей левого ребёнка ДО слияния
        int leftOriginalCount = leftChild.getKeyCount();

        // Спускаем ключ родителя в левого ребёнка
        leftChild.setKey(leftChild.getKeyCount(), parent.getKey(index));
        leftChild.setValue(leftChild.getKeyCount(), parent.getValue(index));
        leftChild.setKeyCount(leftChild.getKeyCount() + 1);

        // Переносим ключи и значения правого соседа в левого
        for (int i = 0; i < rightChild.getKeyCount(); i++) {
            leftChild.setKey(leftChild.getKeyCount(), rightChild.getKey(i));
            leftChild.setValue(leftChild.getKeyCount(), rightChild.getValue(i));
            leftChild.setKeyCount(leftChild.getKeyCount() + 1);
        }

        // Переносим детей правого соседа — начиная с leftOriginalCount + 1
        if (!leftChild.isLeaf()) {
            for (int i = 0; i <= rightChild.getKeyCount(); i++) {
                leftChild.setChild(leftOriginalCount + 1 + i, rightChild.getChild(i));
            }
        }

        // Удаляем ключ родителя
        parent.removeKey(index);

        // Сдвигаем детей родителя
        for (int i = index + 1; i < parent.getKeyCount() + 1; i++) {
            parent.setChild(i, parent.getChild(i + 1));
        }
        parent.setChild(parent.getKeyCount() + 1, null);
    }

    // ============================================================
    //                     ОБХОД И ДИАПАЗОН
    // ============================================================

    /** Обходит все значения в порядке возрастания ключей. */
    public void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        traverse(root, action);
    }

    private void traverse(BTreeNode<K, T> node, Consumer<? super T> action) {
        for (int i = 0; i < node.getKeyCount(); i++) {
            if (!node.isLeaf()) {
                traverse(node.getChild(i), action);
            }
            action.accept(node.getValue(i));
        }
        if (!node.isLeaf()) {
            traverse(node.getChild(node.getKeyCount()), action);
        }
    }

    /**
     * Диапазонный скан: передаёт action все значения с ключом в [from, to] включительно,
     * в порядке возрастания ключей. Сложность: O(log n + k), где k — число найденных элементов.
     */
    public void forEachInRange(K from, K to, Consumer<? super T> action) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(action, "action");
        if (from.compareTo(to) > 0) {
            return;
        }
        rangeScanInNode(root, from, to, action);
    }

    private void rangeScanInNode(BTreeNode<K, T> node, K from, K to, Consumer<? super T> action) {
        int i = 0;
        while (i < node.getKeyCount()) {
            K key = node.getKey(i);
            int cmpFrom = key.compareTo(from);
            int cmpTo = key.compareTo(to);

            // Левый ребёнок содержит ключи меньше key; нужен, только если key > from
            if (cmpFrom > 0 && !node.isLeaf()) {
                rangeScanInNode(node.getChild(i), from, to, action);
            }

            if (cmpFrom >= 0 && cmpTo <= 0) {
                action.accept(node.getValue(i));
            }

            // Ключ больше верхней границы — правее искать нечего
            if (cmpTo > 0) {
                return;
            }

            i++;
        }

        // Правый ребёнок последнего ключа
        if (!node.isLeaf()) {
            rangeScanInNode(node.getChild(node.getKeyCount()), from, to, action);
        }
    }

    // ============================================================
    //                     СТАТИСТИКА
    // ============================================================

    /** Накопитель для обхода при подсчёте статистики. */
    private static final class StatsCounter {
        int nodes;
        int leaves;
        int internal;
        int keys;
        final int[] histogram = new int[MAX_KEYS + 1];
    }

    /**
     * Статистика дерева (один обход, O(n)).
     */
    public TreeStats getStats() {
        if (size == 0) {
            return TreeStats.empty();
        }
        StatsCounter c = new StatsCounter();
        int height = collectStats(root, c);
        return new TreeStats(c.nodes, c.leaves, c.internal, height, c.keys, c.histogram);
    }

    private int collectStats(BTreeNode<K, T> node, StatsCounter c) {
        c.nodes++;
        c.keys += node.getKeyCount();
        c.histogram[node.getKeyCount()]++;
        if (node.isLeaf()) {
            c.leaves++;
            return 1;
        }
        c.internal++;
        int maxHeight = 0;
        for (int i = 0; i <= node.getKeyCount(); i++) {
            BTreeNode<K, T> child = node.getChild(i);
            if (child != null) {
                maxHeight = Math.max(maxHeight, collectStats(child, c));
            }
        }
        return maxHeight + 1;
    }

    // ============================================================
    //                     САМОПРОВЕРКА СТРУКТУРЫ
    // ============================================================

    /** Накопитель для validateStructure. */
    private static final class Validation {
        int keys;
        int leafDepth = -1;
    }

    /**
     * Проверяет инварианты B-дерева: порядок ключей, границы в поддеревьях,
     * число ключей и детей в узлах, одинаковую глубину листьев, соответствие size.
     * @return null, если дерево корректно, иначе описание первой найденной проблемы
     */
    public String validateStructure() {
        Validation v = new Validation();
        String error = validateNode(root, true, 0, null, null, v);
        if (error != null) {
            return error;
        }
        if (v.keys != size) {
            return "size() = " + size + ", а в узлах найдено ключей: " + v.keys;
        }
        return null;
    }

    private String validateNode(BTreeNode<K, T> node, boolean isRoot, int depth,
                                K lo, K hi, Validation v) {
        if (node == null) {
            return "ссылка на узел равна null";
        }
        if (depth > MAX_DEPTH_GUARD) {
            return "глубина больше " + MAX_DEPTH_GUARD + " (возможен цикл)";
        }
        int k = node.getKeyCount();
        if (k < 0 || k > MAX_KEYS) {
            return "недопустимое число ключей " + k;
        }
        if (!isRoot && k < MIN_KEYS) {
            return "в узле " + node + " ключей меньше минимума";
        }
        if (isRoot && !node.isLeaf() && k == 0) {
            return "внутренний корень без ключей";
        }
        v.keys += k;

        for (int i = 0; i < k; i++) {
            K key = node.getKey(i);
            if (key == null) {
                return "null-ключ в узле " + node;
            }
            if (i > 0 && node.getKey(i - 1).compareTo(key) >= 0) {
                return "ключи не по возрастанию в узле " + node;
            }
            if (lo != null && key.compareTo(lo) <= 0) {
                return "ключ " + key + " нарушает нижнюю границу поддерева";
            }
            if (hi != null && key.compareTo(hi) >= 0) {
                return "ключ " + key + " нарушает верхнюю границу поддерева";
            }
            T value = node.getValue(i);
            if (value == null || keyExtractor.apply(value).compareTo(key) != 0) {
                return "значение не соответствует ключу " + key;
            }
        }

        if (node.isLeaf()) {
            if (v.leafDepth == -1) {
                v.leafDepth = depth;
            } else if (v.leafDepth != depth) {
                return "листья расположены на разных уровнях";
            }
            return null;
        }

        for (int i = 0; i <= k; i++) {
            K childLo = (i == 0) ? lo : node.getKey(i - 1);
            K childHi = (i == k) ? hi : node.getKey(i);
            String error = validateNode(node.getChild(i), false, depth + 1, childLo, childHi, v);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "BTreeStore{size=" + size + "}";
    }
}
