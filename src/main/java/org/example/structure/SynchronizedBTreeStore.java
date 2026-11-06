package org.example.structure;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Потокобезопасная обёртка над BTreeStore (паттерн «декоратор»).
 * Запись (add/remove) берёт эксклюзивную блокировку, чтение (find, обходы, статистика) —
 * общую, поэтому читатели друг другу не мешают, но никогда не видят дерево «посередине» изменения.
 *
 * Внимание: callback, переданный в forEach/forEachInRange, выполняется под блокировкой чтения,
 * поэтому внутри него нельзя вызывать add/removeByKey этого же хранилища (будет deadlock).
 */
public class SynchronizedBTreeStore<K extends Comparable<K>, T> implements EntityStore<K, T> {

    private final BTreeStore<K, T> store;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SynchronizedBTreeStore(Function<? super T, ? extends K> keyExtractor) {
        this.store = new BTreeStore<>(keyExtractor);
    }

    @Override
    public void add(T entity) {
        write(() -> store.add(entity));
    }

    @Override
    public T findByKey(K key) {
        return read(() -> store.findByKey(key));
    }

    @Override
    public boolean removeByKey(K key) {
        return writeResult(() -> store.removeByKey(key));
    }

    @Override
    public int size() {
        return read(store::size);
    }

    public void forEach(Consumer<? super T> action) {
        read(() -> {
            store.forEach(action);
            return null;
        });
    }

    public void forEachInRange(K from, K to, Consumer<? super T> action) {
        read(() -> {
            store.forEachInRange(from, to, action);
            return null;
        });
    }

    public TreeStats getStats() {
        return read(store::getStats);
    }

    public String validateStructure() {
        return read(store::validateStructure);
    }

    // ---------------------- блокировки ----------------------

    private <R> R read(Supplier<R> action) {
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void write(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Запись с результатом: removeByKey меняет структуру, поэтому берёт блокировку записи. */
    private <R> R writeResult(Supplier<R> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
