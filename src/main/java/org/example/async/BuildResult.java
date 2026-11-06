package org.example.async;

import org.example.model.Product;
import org.example.structure.BTreeStore;

import java.util.List;

/**
 * Результат фоновой задачи, строившей новое дерево (загрузка или генерация).
 * Дерево целиком собирается в фоновом потоке и только потом передаётся в GUI-поток,
 * поэтому два потока никогда не работают с одним деревом одновременно.
 */
public final class BuildResult {

    private final BTreeStore<Integer, Product> tree;
    private final List<String> errors;
    private final long elapsedMillis;

    public BuildResult(BTreeStore<Integer, Product> tree, List<String> errors, long elapsedMillis) {
        this.tree = tree;
        this.errors = List.copyOf(errors);
        this.elapsedMillis = elapsedMillis;
    }

    public BTreeStore<Integer, Product> getTree() {
        return tree;
    }

    /** Сообщения о пропущенных строках и дубликатах (пусто, если всё прошло гладко). */
    public List<String> getErrors() {
        return errors;
    }

    public int getSkippedCount() {
        return errors.size();
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }
}
