package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.async.BuildResult;
import org.example.async.GenerateTask;
import org.example.async.LoadTask;
import org.example.io.CsvLoader;
import org.example.model.DiscontinuedProduct;
import org.example.model.Editable;
import org.example.model.Product;
import org.example.model.WarrantyProduct;
import org.example.structure.BTreeNode;
import org.example.structure.BTreeStore;
import org.example.structure.DataGenerator;
import org.example.structure.TreeStats;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Главное окно приложения «Каталог магазина».
 * Основное хранилище — BTreeStore (B-дерево порядка 4).
 * Долгие операции (загрузка, генерация, сохранение) идут в фоне через Task с прогрессом и отменой;
 * дерево строится в фоновом потоке и подменяется в GUI-потоке целиком, без общего доступа из двух потоков.
 */
public class MainView {

    /** Сколько сообщений об ошибках показывать в окне (остальные — «и ещё N»). */
    private static final int MAX_SHOWN_ERRORS = 200;

    /** Максимум товаров за одну генерацию. */
    private static final int MAX_GENERATE = 500_000;

    private final Stage stage;

    /** Основное хранилище — B-дерево. */
    private BTreeStore<Integer, Product> tree = newTree();

    /** Список для отображения в таблице (только слой отображения). */
    private final ObservableList<Product> products = FXCollections.observableArrayList();

    private final TableView<Product> table = new TableView<>();
    private final CsvLoader csvLoader = new CsvLoader();

    private Button editBtn;
    private Button deleteBtn;
    private final Label statusLabel = new Label("Готово");

    private final TextField findField = new TextField();
    private final TextField rangeFromField = new TextField();
    private final TextField rangeToField = new TextField();

    /** Окно визуализации; создаётся при первом показе и потом переиспользуется. */
    private TreeViewWindow treeWindow;

    public MainView(Stage stage) {
        this.stage = stage;
        buildUI();
    }

    private static BTreeStore<Integer, Product> newTree() {
        return new BTreeStore<>(Product::getArticle);
    }

    private void buildUI() {
        table.setItems(products);

        TableColumn<Product, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(p.getClass().getSimpleName());
        });
        typeCol.setPrefWidth(140);

        TableColumn<Product, Integer> articleCol = new TableColumn<>("Артикул");
        articleCol.setCellValueFactory(new PropertyValueFactory<>("article"));
        articleCol.setPrefWidth(80);

        TableColumn<Product, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<Product, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(140);

        TableColumn<Product, Double> priceCol = new TableColumn<>("Цена");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(100);

        TableColumn<Product, Integer> stockCol = new TableColumn<>("Остаток");
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stock"));
        stockCol.setPrefWidth(80);

        TableColumn<Product, String> warrantyCol = new TableColumn<>("Гарантия (мес.)");
        warrantyCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            String text = (p instanceof WarrantyProduct wp)
                    ? String.valueOf(wp.getWarrantyMonths())
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        warrantyCol.setPrefWidth(120);

        TableColumn<Product, String> discontinuedCol = new TableColumn<>("Снят с продажи");
        discontinuedCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            String text = (p instanceof DiscontinuedProduct dp && dp.getDiscontinuedDate() != null)
                    ? dp.getDiscontinuedDate().toString()
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        discontinuedCol.setPrefWidth(130);

        table.getColumns().addAll(typeCol, articleCol, nameCol, categoryCol,
                priceCol, stockCol, warrantyCol, discontinuedCol);

        table.setPlaceholder(new Label("Нет данных. Нажмите «Загрузить пример» или «Сгенерировать N»."));

        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateButtonsState()
        );

        // ---------- Панель поиска и диапазона ----------
        findField.setPromptText("Артикул");
        findField.setPrefWidth(100);
        findField.setOnAction(e -> onFind());
        Button findBtn = new Button("Найти");
        findBtn.setOnAction(e -> onFind());

        rangeFromField.setPromptText("от");
        rangeFromField.setPrefWidth(80);
        rangeToField.setPromptText("до");
        rangeToField.setPrefWidth(80);
        Button rangeBtn = new Button("Показать диапазон");
        rangeBtn.setOnAction(e -> onRange());
        Button showAllBtn = new Button("Показать все");
        showAllBtn.setOnAction(e -> {
            refreshTable(null);
            setStatus("Показаны все товары: " + tree.size());
        });

        HBox searchBar = new HBox(10,
                new Label("Поиск по артикулу:"), findField, findBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Диапазон артикулов:"), rangeFromField, rangeToField, rangeBtn, showAllBtn);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(10));

        // ---------- Кнопки операций ----------
        Button loadExampleBtn = new Button("Загрузить пример");
        Button loadBtn = new Button("Загрузить из CSV");
        Button saveBtn = new Button("Сохранить в CSV");
        Button addBtn = new Button("Добавить");
        editBtn = new Button("Изменить");
        deleteBtn = new Button("Удалить");
        Button statsBtn = new Button("Статистика");
        Button showTreeBtn = new Button("Показать дерево");
        Button generateBtn = new Button("Сгенерировать N");

        loadExampleBtn.setOnAction(e -> loadFromResource("/products.csv"));
        loadBtn.setOnAction(e -> loadFromFileChooser());
        saveBtn.setOnAction(e -> saveToFileChooser());
        addBtn.setOnAction(e -> onAdd());
        editBtn.setOnAction(e -> onEdit());
        deleteBtn.setOnAction(e -> onDelete());
        statsBtn.setOnAction(e -> onShowStats());
        showTreeBtn.setOnAction(e -> showTreeWindow(Set.of()));
        generateBtn.setOnAction(e -> onGenerate());

        HBox buttons = new HBox(10,
                loadExampleBtn, loadBtn, saveBtn, addBtn, editBtn, deleteBtn,
                statsBtn, showTreeBtn, generateBtn);
        buttons.setPadding(new Insets(10));

        statusLabel.setPadding(new Insets(0, 10, 10, 10));

        BorderPane root = new BorderPane();
        root.setTop(searchBar);
        root.setCenter(table);
        root.setBottom(new VBox(buttons, statusLabel));

        Scene scene = new Scene(root, 1300, 650);
        stage.setTitle("Каталог магазина (B-дерево)");
        stage.setScene(scene);

        updateButtonsState();
    }

    public void show() {
        stage.show();
    }

    // ============================================================
    //                  ТАБЛИЦА, СТАТУС, ВИЗУАЛИЗАЦИЯ
    // ============================================================

    /**
     * Перезаполняет таблицу всеми товарами из дерева (в порядке возрастания артикула).
     * @param toSelect товар, который нужно выделить и показать (может быть null)
     */
    private void refreshTable(Product toSelect) {
        List<Product> all = new ArrayList<>(tree.size());
        tree.forEach(all::add);
        products.setAll(all);
        if (toSelect != null) {
            table.getSelectionModel().select(toSelect);
            table.scrollTo(toSelect);
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void updateButtonsState() {
        Product selected = table.getSelectionModel().getSelectedItem();
        boolean isEditable = selected instanceof Editable;
        editBtn.setDisable(!isEditable);
        deleteBtn.setDisable(selected == null);
    }

    private static Set<BTreeNode<?, ?>> newVisitedSet() {
        // Узлы сравниваем по ссылке, а не по equals
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /** Открывает окно визуализации (или выводит его вперёд) и рисует дерево. */
    private void showTreeWindow(Set<BTreeNode<?, ?>> highlighted) {
        if (treeWindow == null) {
            treeWindow = new TreeViewWindow(stage);
        }
        treeWindow.show(tree, highlighted);
    }

    /** Перерисовывает окно визуализации после операции, если оно открыто. */
    private void updateTreeView(Set<BTreeNode<?, ?>> highlighted) {
        if (treeWindow != null && treeWindow.isShowing()) {
            treeWindow.update(tree, highlighted);
        }
    }

    // ============================================================
    //                           ПОИСК
    // ============================================================

    private void onFind() {
        Integer key = parseInt(findField.getText(), "Артикул");
        if (key == null) return;

        Set<BTreeNode<?, ?>> visited = newVisitedSet();
        Product found = tree.findByKey(key, visited::add);

        if (found != null) {
            refreshTable(found);
            setStatus("Найден: " + found.getName() + " (артикул " + key + "). Просмотрено узлов: " + visited.size());
        } else {
            table.getSelectionModel().clearSelection();
            setStatus("Артикул " + key + " не найден. Просмотрено узлов: " + visited.size());
        }
        showTreeWindow(visited);   // подсветка пути поиска
    }

    // ============================================================
    //                   ДИАПАЗОННЫЙ СКАН
    // ============================================================

    private void onRange() {
        Integer from = parseInt(rangeFromField.getText(), "от");
        if (from == null) return;
        Integer to = parseInt(rangeToField.getText(), "до");
        if (to == null) return;
        if (from > to) {
            showError("Диапазон", "Начало диапазона больше конца.");
            return;
        }

        List<Product> found = new ArrayList<>();
        long start = System.nanoTime();
        tree.forEachInRange(from, to, found::add);
        long micros = (System.nanoTime() - start) / 1_000;

        products.setAll(found);
        setStatus("Диапазон [" + from + "; " + to + "]: найдено " + found.size()
                + ", время " + micros + " мкс. «Показать все» возвращает полный список.");
    }

    /** Разбирает целое число из поля ввода; при ошибке показывает сообщение и возвращает null. */
    private Integer parseInt(String text, String fieldName) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            showError("Ошибка ввода", "Поле «" + fieldName + "»: ожидается целое число.");
            return null;
        }
    }

    // ============================================================
    //                        ДОБАВЛЕНИЕ
    // ============================================================

    private void onAdd() {
        ProductDialog dialog = new ProductDialog(stage, null);
        Optional<Product> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        Product product = result.get();
        Set<BTreeNode<?, ?>> visited = newVisitedSet();
        try {
            tree.add(product, visited::add);
        } catch (IllegalArgumentException duplicate) {
            showError("Дубликат артикула",
                    "Товар с артикулом " + product.getArticle() + " уже существует.");
            return;
        }
        refreshTable(product);
        updateTreeView(visited);
        setStatus("Добавлен: " + product.getName() + ". Всего: " + tree.size()
                + ". Просмотрено узлов: " + visited.size());
    }

    // ============================================================
    //                       РЕДАКТИРОВАНИЕ
    // ============================================================

    private void onEdit() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!(selected instanceof Editable)) {
            showError("Нельзя редактировать",
                    "Товар «" + selected.getName() + "» доступен только для чтения.");
            return;
        }

        ProductDialog dialog = new ProductDialog(stage, selected);
        Optional<Product> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        Product updated = result.get();
        int oldKey = selected.getArticle();

        // Если пользователь сменил артикул на уже занятый — отказываем, ничего не трогая
        if (updated.getArticle() != oldKey && tree.findByKey(updated.getArticle()) != null) {
            showError("Дубликат артикула",
                    "Товар с артикулом " + updated.getArticle() + " уже существует. Изменения не применены.");
            return;
        }

        Set<BTreeNode<?, ?>> visited = newVisitedSet();
        tree.removeByKey(oldKey);
        tree.add(updated, visited::add);
        refreshTable(updated);
        updateTreeView(visited);
        setStatus("Изменён: " + updated.getName());
    }

    // ============================================================
    //                         УДАЛЕНИЕ
    // ============================================================

    private void onDelete() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Подтверждение удаления");
        confirm.setHeaderText("Удалить товар?");
        confirm.setContentText("Товар: " + selected.getName() +
                " (артикул " + selected.getArticle() + ")");

        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            boolean removed = tree.removeByKey(selected.getArticle());
            if (removed) {
                refreshTable(null);
                updateTreeView(Set.of());
                setStatus("Удалён: " + selected.getName() + ". Всего: " + tree.size());
            } else {
                showError("Ошибка", "Не удалось удалить товар.");
            }
        }
    }

    // ============================================================
    //                       СТАТИСТИКА
    // ============================================================

    private void onShowStats() {
        TreeStats stats = tree.getStats();
        StringBuilder message = new StringBuilder(String.format(
                "Статистика B-дерева:%n" +
                        "  Всего узлов: %d%n" +
                        "  Листьев: %d%n" +
                        "  Внутренних: %d%n" +
                        "  Высота: %d%n" +
                        "  Среднее ключей в узле: %.2f%n" +
                        "  Заполненность: %.1f%%%n" +
                        "  Полных узлов: %d%n" +
                        "  Неполных узлов: %d%n" +
                        "  Всего элементов: %d%n%n" +
                        "Распределение узлов по числу ключей:",
                stats.getTotalNodes(),
                stats.getLeafNodes(),
                stats.getInternalNodes(),
                stats.getHeight(),
                stats.getAvgKeysPerNode(),
                stats.getFillFactor() * 100,
                stats.getFullNodes(),
                stats.getNotFullNodes(),
                stats.getTotalKeys()));

        int[] histogram = stats.getKeysHistogram();
        for (int keys = 1; keys <= BTreeNode.MAX_KEYS; keys++) {
            message.append(String.format("%n  узлов с %d ключ(ами): %d", keys, histogram[keys]));
        }
        showInfo(message.toString());
    }

    // ============================================================
    //                       ГЕНЕРАЦИЯ ДАННЫХ
    // ============================================================

    private void onGenerate() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.initOwner(stage);
        dialog.setTitle("Генерация данных");
        dialog.setHeaderText("Сколько товаров сгенерировать?");
        dialog.setContentText("Число:");

        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) return;

        int count;
        try {
            count = Integer.parseInt(input.get().trim());
        } catch (NumberFormatException ex) {
            showError("Ошибка", "Неверный формат числа: " + input.get());
            return;
        }
        if (count <= 0) {
            showError("Ошибка", "Число должно быть положительным.");
            return;
        }
        if (count > MAX_GENERATE) {
            showError("Ошибка", "Максимум " + MAX_GENERATE + " товаров за раз.");
            return;
        }

        final int generated = count;
        GenerateTask task = new GenerateTask(tree, new DataGenerator(System.nanoTime()), generated);
        runTask(task, "Генерация данных", true, result -> {
            tree = result.getTree();
            refreshTable(null);
            updateTreeView(Set.of());
            setStatus("Сгенерировано: " + generated + " товаров за " + result.getElapsedMillis()
                    + " мс. Всего: " + tree.size());
        });
    }

    // ============================================================
    //                     ЗАГРУЗКА ИЗ CSV (с Task)
    // ============================================================

    private void loadFromResource(String resourcePath) {
        runTask(LoadTask.fromResource(resourcePath), "Загрузка примера", true,
                result -> applyLoadResult(result, "Встроенный пример"));
    }

    private void loadFromFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите CSV-файл");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;

        runTask(LoadTask.fromFile(file.toPath()), "Загрузка файла: " + file.getName(), true,
                result -> applyLoadResult(result, file.getName()));
    }

    /** Подменяет дерево уже готовым (оно построено в фоне) и показывает итог. */
    private void applyLoadResult(BuildResult result, String source) {
        tree = result.getTree();
        refreshTable(null);
        updateTreeView(Set.of());

        String summary = source + ": загружено " + tree.size() + " товаров за "
                + result.getElapsedMillis() + " мс";
        if (result.getErrors().isEmpty()) {
            setStatus(summary);
        } else {
            setStatus(summary + ", пропущено: " + result.getSkippedCount());
            showErrorsDialog("Загружено с ошибками",
                    summary + "\nПропущено строк: " + result.getSkippedCount(),
                    result.getErrors());
        }
    }

    // ============================================================
    //                     СОХРАНЕНИЕ В CSV
    // ============================================================

    private void saveToFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить в CSV");
        fc.setInitialFileName("products_export.csv");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));

        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        // Снимок данных делаем в GUI-потоке, а запись на диск — в фоне
        List<Product> snapshot = new ArrayList<>(tree.size());
        tree.forEach(snapshot::add);

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                updateMessage("Сохранение: " + file.getName());
                updateProgress(-1, -1);
                csvLoader.save(snapshot, file.toPath());
                return snapshot.size();
            }
        };
        runTask(task, "Сохранение файла", false,
                saved -> setStatus("Файл сохранён: " + file.getName() + ", товаров: " + saved));
    }

    // ============================================================
    //                    ФОНОВЫЕ ЗАДАЧИ
    // ============================================================

    /**
     * Показывает модальный диалог с прогрессом и запускает Task в фоне.
     *
     * @param cancellable можно ли отменить задачу (кнопка «Отмена» и закрытие окна)
     * @param onSuccess   вызывается в GUI-потоке с результатом, если задача завершилась успешно
     */
    private <T> void runTask(Task<T> task, String title, boolean cancellable, Consumer<T> onSuccess) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setPrefWidth(350);

        Label messageLabel = new Label();
        messageLabel.textProperty().bind(task.messageProperty());

        VBox content = new VBox(10, messageLabel, progressBar);
        content.setPadding(new Insets(15));
        if (cancellable) {
            Button cancelBtn = new Button("Отмена");
            cancelBtn.setOnAction(e -> task.cancel());
            content.getChildren().add(cancelBtn);
        }

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setScene(new Scene(content, 420, 150));
        dialog.setOnCloseRequest(e -> {
            if (cancellable) {
                task.cancel();
            } else {
                e.consume();   // сохранение нельзя прервать закрытием окна
            }
        });

        task.setOnSucceeded(e -> {
            dialog.close();
            T value = task.getValue();
            if (value != null) {
                onSuccess.accept(value);
            }
        });

        task.setOnFailed(e -> {
            dialog.close();
            Throwable ex = task.getException();
            showError("Ошибка", ex == null ? "Неизвестная ошибка"
                    : (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        });

        task.setOnCancelled(e -> {
            dialog.close();
            setStatus("Операция отменена, данные не изменены");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        dialog.show();
    }

    // ============================================================
    //                        ДИАЛОГИ
    // ============================================================

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Предупреждение с раскрывающимся прокручиваемым списком сообщений (первые MAX_SHOWN_ERRORS). */
    private void showErrorsDialog(String title, String header, List<String> lines) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(lines.size(), MAX_SHOWN_ERRORS);
        for (int i = 0; i < shown; i++) {
            text.append(lines.get(i)).append('\n');
        }
        if (lines.size() > shown) {
            text.append("… и ещё ").append(lines.size() - shown);
        }

        TextArea area = new TextArea(text.toString());
        area.setEditable(false);
        area.setPrefRowCount(12);
        area.setPrefColumnCount(60);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.getDialogPane().setExpandableContent(area);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }
}
