// Пакет structure — структуры данных
package org.example.structure;

/**
 * Узел B-дерева порядка 4.
 * Узел хранит: до 3 ключей, до 3 значений, до 4 детей, счётчик и флаг листа.
 */
public class BTreeNode<K extends Comparable<K>, T> {

    /** Максимум ключей в узле (порядок - 1 = 3). */
    public static final int MAX_KEYS = 3;

    /** Максимум детей в узле (порядок = 4). */
    public static final int MAX_CHILDREN = 4;

    /** Массив ключей. Object[] — потому что дженерик-массив создать нельзя. */
    private final Object[] keys;

    /** Массив значений — параллельно ключам. */
    private final Object[] values;

    /** Массив детей. */
    private final BTreeNode<K, T>[] children;

    /** Сколько ключей занято (0..MAX_KEYS). */
    private int keyCount;

    /** Является ли узел листом. */
    private boolean leaf;

    /** Конструктор узла. leaf=true — лист, false — внутренний. */
    @SuppressWarnings("unchecked")
    public BTreeNode(boolean leaf) {
        this.keys = new Object[MAX_KEYS];
        this.values = new Object[MAX_KEYS];
        this.children = new BTreeNode[MAX_CHILDREN];
        this.keyCount = 0;
        this.leaf = leaf;
    }

    /** true, если узел заполнен (3 ключа). */
    public boolean isFull() {
        return keyCount == MAX_KEYS;
    }

    /** true, если узел — лист. */
    public boolean isLeaf() {
        return leaf;
    }

    /** Количество ключей в узле. */
    public int getKeyCount() {
        return keyCount;
    }

    /** Устанавливает счётчик ключей (для внутреннего использования). */
    public void setKeyCount(int count) {
        this.keyCount = count;
    }

    /** Ключ по индексу. */
    @SuppressWarnings("unchecked")
    public K getKey(int index) {
        return (K) keys[index];
    }

    /** Значение по индексу. */
    @SuppressWarnings("unchecked")
    public T getValue(int index) {
        return (T) values[index];
    }

    /** Устанавливает ключ по индексу. */
    public void setKey(int index, K key) {
        keys[index] = key;
    }

    /** Устанавливает значение по индексу. */
    public void setValue(int index, T value) {
        values[index] = value;
    }

    /** Ребёнок по индексу. */
    public BTreeNode<K, T> getChild(int index) {
        return children[index];
    }

    /** Устанавливает ребёнка по индексу. */
    public void setChild(int index, BTreeNode<K, T> child) {
        children[index] = child;
    }

    /**
     * Ищет индекс ключа в узле.
     * Если ключ найден — возвращает его индекс.
     * Если не найден — возвращает индекс, КУДА его вставлять.
     */
    public int findKeyIndex(K key) {
        int i = 0;
        while (i < keyCount && getKey(i).compareTo(key) < 0) {
            i++;
        }
        return i;
    }

    /**
     * Вставляет пару (ключ, значение) в узел.
     * Предполагается, что узел НЕ полон.
     */
    public void insertKey(K key, T value) {
        int i = findKeyIndex(key);
        for (int j = keyCount; j > i; j--) {
            keys[j] = keys[j - 1];
            values[j] = values[j - 1];
        }
        keys[i] = key;
        values[i] = value;
        keyCount++;
    }

    /**
     * Удаляет ключ и значение по индексу, сдвигая остальные влево.
     */
    public void removeKey(int index) {
        for (int i = index; i < keyCount - 1; i++) {
            keys[i] = keys[i + 1];
            values[i] = values[i + 1];
        }
        keys[keyCount - 1] = null;
        values[keyCount - 1] = null;
        keyCount--;
    }

    /** Строковое представление для отладки. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keyCount; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(getKey(i));
        }
        sb.append("]");
        return sb.toString();
    }
}