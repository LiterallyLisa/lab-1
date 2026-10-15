package org.example.structure;

/**
 * B-дерево с synchronized-защитой.
 * Используется для демонстрации корректной многопоточной работы.
 */
public class SynchronizedBTreeStore extends BTreeStore<Integer, org.example.model.Product> {

    @Override
    public synchronized void add(org.example.model.Product entity) {
        super.add(entity);
    }

    @Override
    public synchronized org.example.model.Product findByKey(Integer key) {
        return super.findByKey(key);
    }

    @Override
    public synchronized boolean removeByKey(Integer key) {
        return super.removeByKey(key);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}