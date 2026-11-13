package org.example.serialization;

import org.example.model.Product;
import org.example.structure.BTreeStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Строит B-дерево из прочитанных данных. Дубликаты артикулов пропускаются и попадают в список ошибок,
 * как и при загрузке CSV. Не зависит от JavaFX, поэтому проверяется обычными JUnit-тестами.
 */
public final class CatalogImporter {

    /** Слушатель прогресса; вернув false, прерывает построение. */
    @FunctionalInterface
    public interface ProgressListener {
        boolean onProgress(int done, int total);
    }

    /** Результат: готовое дерево и сообщения о пропущенных записях. */
    public record Result(BTreeStore<Integer, Product> tree, List<String> errors) {
    }

    private static final int REPORT_EVERY = 1024;

    private CatalogImporter() {
    }

    /** @throws CancellationException если слушатель вернул false */
    public static Result buildTree(CatalogData data, ProgressListener listener) {
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        List<String> errors = new ArrayList<>();
        int total = data.products().size();
        int done = 0;
        for (ProductRecord record : data.products()) {
            Product p = ProductMapper.toProduct(record);
            try {
                tree.add(p);
            } catch (IllegalArgumentException duplicate) {
                errors.add("Артикул " + p.getArticle() + " повторяется — запись пропущена");
            }
            done++;
            if (listener != null && done % REPORT_EVERY == 0 && !listener.onProgress(done, total)) {
                throw new CancellationException();
            }
        }
        if (listener != null) {
            listener.onProgress(total, total);
        }
        return new Result(tree, errors);
    }

    public static Result buildTree(CatalogData data) {
        return buildTree(data, null);
    }
}
