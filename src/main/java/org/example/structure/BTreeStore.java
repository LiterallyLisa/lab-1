// Пакет structure
package org.example.structure;

import org.example.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * B-дерево порядка 4 — хранилище пар ключ-значение.
 * Реализует интерфейс EntityStore<K, T>.
 *
 * Порядок 4:
 *  - до 3 ключей в узле;
 *  - до 4 детей в узле;
 *  - все листья на одном уровне.
 */
public class BTreeStore<K extends Comparable<K>, T> implements EntityStore<K, T> {

    /** Порядок B-дерева. */
    public static final int ORDER = 4;

    /** Максимум ключей в узле (order - 1). */
    public static final int MAX_KEYS = ORDER - 1;

    /** Максимум детей в узле (order). */
    public static final int MAX_CHILDREN = ORDER;

    /** Минимум ключей в узле (кроме корня). */
    public static final int MIN_KEYS = (ORDER + 1) / 2 - 1;

    /** Корень дерева. */
    private BTreeNode<K, T> root;

    /** Количество элементов в дереве. */
    private int size;

    /** Конструктор: создаём пустое дерево. */
    public BTreeStore() {
        this.root = new BTreeNode<>(true);
        this.size = 0;
    }

    // ============================================================
    //                     ПУБЛИЧНЫЕ МЕТОДЫ
    // ============================================================

    /**
     * Добавить значение. Ключ берётся из самого объекта.
     */
    @Override
    public void add(T entity) {
        K key = extractKey(entity);
        if (root.isFull()) {
            BTreeNode<K, T> newRoot = new BTreeNode<>(false);
            newRoot.setChild(0, root);
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, key, entity);
        size++;
    }

    /**
     * Найти значение по ключу.
     */
    @Override
    public T findByKey(K key) {
        return findByKeyInNode(root, key);
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
            if (root.getKeyCount() == 0 && !root.isLeaf()) {
                root = root.getChild(0);
            }
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

    // ============================================================
    //                     ПОИСК
    // ============================================================

    private T findByKeyInNode(BTreeNode<K, T> node, K key) {
        int index = node.findKeyIndex(key);
        if (index < node.getKeyCount() && node.getKey(index).compareTo(key) == 0) {
            return node.getValue(index);
        }
        if (node.isLeaf()) {
            return null;
        }
        return findByKeyInNode(node.getChild(index), key);
    }

    // ============================================================
    //                     ВСТАВКА
    // ============================================================

    private void insertNonFull(BTreeNode<K, T> node, K key, T value) {
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
            insertNonFull(node.getChild(index), key, value);
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
            }
        }
        for (int i = parent.getKeyCount(); i > childIndex; i--) {
            parent.setChild(i + 1, parent.getChild(i));
        }
        parent.setChild(childIndex + 1, newChild);
        parent.insertKey(fullChild.getKey(mid), fullChild.getValue(mid));
        fullChild.setKeyCount(mid);
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

        // Упреждающее обеспечение
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
            K predKey = getPredecessorKey(leftChild);
            T predValue = getPredecessorValue(leftChild);
            node.setKey(index, predKey);
            node.setValue(index, predValue);
            return removeFromNode(leftChild, predKey);
        } else if (rightChild.getKeyCount() > MIN_KEYS) {
            K succKey = getSuccessorKey(rightChild);
            T succValue = getSuccessorValue(rightChild);
            node.setKey(index, succKey);
            node.setValue(index, succValue);
            return removeFromNode(rightChild, succKey);
        } else {
            mergeChildren(node, index);
            return removeFromNode(leftChild, key);
        }
    }

    private K getPredecessorKey(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(node.getKeyCount());
        }
        return node.getKey(node.getKeyCount() - 1);
    }

    private T getPredecessorValue(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(node.getKeyCount());
        }
        return node.getValue(node.getKeyCount() - 1);
    }

    private K getSuccessorKey(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(0);
        }
        return node.getKey(0);
    }

    private T getSuccessorValue(BTreeNode<K, T> node) {
        while (!node.isLeaf()) {
            node = node.getChild(0);
        }
        return node.getValue(0);
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

        // СОХРАНЯЕМ исходное количество ключей левого ребёнка ДО слияния
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
    //                ДОПОЛНИТЕЛЬНЫЕ ОПЕРАЦИИ
    // ============================================================

    /**
     * Диапазонный скан: найти все значения с ключом в диапазоне [from, to] включительно.
     */
    public List<T> rangeScan(K from, K to) {
        List<T> result = new ArrayList<>();
        rangeScanInNode(root, from, to, result);
        return result;
    }

    private void rangeScanInNode(BTreeNode<K, T> node, K from, K to, List<T> result) {
        int i = 0;
        while (i < node.getKeyCount()) {
            K key = node.getKey(i);
            int cmpFrom = key.compareTo(from);
            int cmpTo = key.compareTo(to);

            // Если ключ >= from и в узле есть левый ребёнок — идём в него
            if (cmpFrom > 0 && !node.isLeaf()) {
                rangeScanInNode(node.getChild(i), from, to, result);
            }

            // Если ключ в диапазоне — добавляем значение
            if (cmpFrom >= 0 && cmpTo <= 0) {
                result.add(node.getValue(i));
            }

            // Если ключ > to — дальше смысла нет
            if (cmpTo > 0) {
                return;
            }

            i++;
        }

        // Правый ребёнок последнего ключа
        if (!node.isLeaf()) {
            rangeScanInNode(node.getChild(node.getKeyCount()), from, to, result);
        }
    }

    /**
     * Статистика дерева.
     */
    public TreeStats getStats() {
        int[] counters = new int[5];   // [0] totalNodes, [1] leaves, [2] internal, [3] full, [4] notFull
        int height = computeHeight(root, counters);
        int totalKeys = size;
        double avg = counters[0] > 0 ? (double) totalKeys / counters[0] : 0;
        return new TreeStats(counters[0], counters[1], counters[2], height, avg, counters[3], counters[4], totalKeys);
    }

    private int computeHeight(BTreeNode<K, T> node, int[] counters) {
        counters[0]++;
        if (node.isLeaf()) {
            counters[1]++;
            if (node.isFull()) counters[3]++;
            else counters[4]++;
            return 1;
        } else {
            counters[2]++;
            if (node.isFull()) counters[3]++;
            else counters[4]++;
            int maxHeight = 0;
            for (int i = 0; i <= node.getKeyCount(); i++) {
                if (node.getChild(i) != null) {
                    int h = computeHeight(node.getChild(i), counters);
                    if (h > maxHeight) maxHeight = h;
                }
            }
            return maxHeight + 1;
        }
    }

    // ============================================================
    //                     ВСПОМОГАТЕЛЬНЫЕ
    // ============================================================

    @SuppressWarnings("unchecked")
    private K extractKey(T entity) {
        return (K) Integer.valueOf(((Product) entity).getArticle());
    }

    @Override
    public String toString() {
        return "BTreeStore{size=" + size + "}";
    }
}