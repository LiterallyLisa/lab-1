package org.example.serialization;

import org.example.model.DiscontinuedProduct;
import org.example.model.Product;
import org.example.model.WarrantyProduct;
import org.example.structure.DataGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сравнение форматов для отчёта: размер файла, время сохранения и загрузки (среднее по RUNS прогонам
 * после одного прогрева). Запуск: ./gradlew formatBenchmark   (необязательный аргумент — число товаров).
 */
public final class FormatBenchmark {

    private static final int DEFAULT_COUNT = 10_000;
    private static final int RUNS = 5;

    /** Копия всего вывода: пишется в файл UTF-8, чтобы результат не зависел от кодировки консоли. */
    private static final StringBuilder LOG = new StringBuilder();
    private static final Path REPORT_FILE = Path.of("build", "format-benchmark.txt");

    private FormatBenchmark() {
    }

    private static void say(String line) {
        LOG.append(line).append(System.lineSeparator());
        System.out.println(line);
    }

    private static void sayf(String format, Object... args) {
        say(String.format(format, args).stripTrailing().replace("%n", ""));
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_COUNT;
        CatalogData data = ProductMapper.toCatalog(mixedProducts(count));
        say("Товаров: " + count + ", прогонов: " + RUNS + " (после прогрева)\n");

        List<Serializer<CatalogData>> candidates = List.of(
                new BinaryCatalogSerializer(),
                new JsonCatalogSerializer(true),
                new JsonCatalogSerializer(false));
        String[] names = {"bin", "json (с отступами)", "json (компактный)"};

        Path dir = Files.createTempDirectory("format-bench");
        sayf("%-20s %12s %14s %14s%n", "Формат", "Размер, Б", "Сохранение, мс", "Загрузка, мс");
        for (int i = 0; i < candidates.size(); i++) {
            Serializer<CatalogData> s = candidates.get(i);
            Path file = dir.resolve("catalog" + i + "." + s.extension());

            s.save(data, file);
            s.load(file);                                   // прогрев JIT

            long saveNanos = 0;
            long loadNanos = 0;
            for (int run = 0; run < RUNS; run++) {
                long t0 = System.nanoTime();
                s.save(data, file);
                long t1 = System.nanoTime();
                CatalogData loaded = s.load(file);
                long t2 = System.nanoTime();
                if (!loaded.equals(data)) {
                    throw new IllegalStateException("Roundtrip нарушен для формата " + names[i]);
                }
                saveNanos += t1 - t0;
                loadNanos += t2 - t1;
            }
            sayf("%-20s %12d %14.1f %14.1f%n", names[i], Files.size(file),
                    saveNanos / 1e6 / RUNS, loadNanos / 1e6 / RUNS);
        }

        say("\nФрагмент JSON (с отступами):");
        printHead(dir.resolve("catalog1.json"), 18);
        say("\nФрагмент bin (первые 48 байт, hex):");
        byte[] head = Files.readAllBytes(dir.resolve("catalog0.bin"));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(48, head.length); i++) {
            hex.append(String.format("%02X ", head[i]));
        }
        say(hex.toString().trim());

        Files.createDirectories(REPORT_FILE.getParent());
        Files.writeString(REPORT_FILE, LOG.toString(), StandardCharsets.UTF_8);
        System.out.println("\nРезультат также сохранён в файл: " + REPORT_FILE.toAbsolutePath());
    }

    /** Товары всех трёх типов поровну, артикулы в случайном порядке. */
    private static List<Product> mixedProducts(int count) {
        DataGenerator gen = new DataGenerator(42);
        List<Product> result = new ArrayList<>(count);
        for (int article : gen.shuffledArticles(count, DataGenerator.DEFAULT_FIRST_ARTICLE)) {
            Product p = gen.createProduct(article);
            switch (article % 3) {
                case 0 -> result.add(p);
                case 1 -> result.add(new WarrantyProduct(article, p.getName(), p.getCategory(),
                        p.getPrice(), p.getStock(), 12 + article % 25));
                default -> result.add(new DiscontinuedProduct(article, p.getName(), p.getCategory(),
                        p.getPrice(), p.getStock(), LocalDate.of(2020, 1, 1).plusDays(article % 1500)));
            }
        }
        return result;
    }

    private static void printHead(Path file, int lines) throws IOException {
        List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < Math.min(lines, all.size()); i++) {
            say(all.get(i));
        }
    }
}
