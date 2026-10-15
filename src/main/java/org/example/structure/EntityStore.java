package org.example.structure;
/**
 * Контракт хранилища с ключом.
 * K — тип ключа (например, Integer для артикула).
 * T — тип значения (например, Product).
 */
public interface EntityStore<K, T> {
    void add(T entity); // Добавить значение. Ключ берётся из самого объекта
    T findByKey(K key); // Найти значение по ключу. Вернуть null, если не найдено
    boolean removeByKey(K key); // Удалить значение по ключу. Вернуть true, если удалено
    int size(); // Количество элементов в хранилище
}
