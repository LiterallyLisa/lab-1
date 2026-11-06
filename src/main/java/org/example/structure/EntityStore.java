package org.example.structure;

/**
 * Контракт хранилища с ключом.
 * K — тип ключа (например, Integer для артикула).
 * T — тип значения (например, Product).
 */
public interface EntityStore<K, T> {

    /**
     * Добавить значение. Ключ берётся из самого объекта.
     *
     * @throws IllegalArgumentException если элемент с таким ключом уже есть (структура при этом не меняется)
     */
    void add(T entity);

    /** Найти значение по ключу. Вернуть null, если не найдено. */
    T findByKey(K key);

    /** Удалить значение по ключу. Вернуть true, если удалено. */
    boolean removeByKey(K key);

    /** Количество элементов в хранилище. */
    int size();
}
