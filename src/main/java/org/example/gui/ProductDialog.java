package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.model.Editable;
import org.example.model.Product;
import org.example.model.WarrantyProduct;

import java.util.List;
import java.util.Optional;

/**
 * Модальный диалог создания/редактирования редактируемого товара (WarrantyProduct).
 * Возвращает созданный/изменённый объект, либо пустой Optional, если пользователь отменил.
 */
public class ProductDialog {

    private final Stage dialog;
    private final TextField articleField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField categoryField = new TextField();
    private final TextField priceField = new TextField();
    private final TextField stockField = new TextField();
    private final TextField warrantyField = new TextField();

    private Product result; // сюда сохраняем результат при OK

    public ProductDialog(Window owner, Product existing) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? "Добавить товар" : "Редактировать товар");

        // --- Форма ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        int row = 0;
        grid.add(new Label("Артикул:"), 0, row);
        grid.add(articleField, 1, row++);
        grid.add(new Label("Название:"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("Категория:"), 0, row);
        grid.add(categoryField, 1, row++);
        grid.add(new Label("Цена:"), 0, row);
        grid.add(priceField, 1, row++);
        grid.add(new Label("Остаток:"), 0, row);
        grid.add(stockField, 1, row++);
        grid.add(new Label("Срок гарантии (мес.):"), 0, row);
        grid.add(warrantyField, 1, row++);

        // --- Кнопки ---
        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Отмена");
        okBtn.setOnAction(e -> onOk());
        cancelBtn.setOnAction(e -> dialog.close());

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(10, okBtn, cancelBtn);
        buttons.setPadding(new Insets(10));

        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setCenter(grid);
        root.setBottom(buttons);

        dialog.setScene(new javafx.scene.Scene(root, 380, 320));

        // --- Если редактируем, заполняем поля ---
        if (existing instanceof WarrantyProduct wp) {
            articleField.setText(String.valueOf(wp.getArticle()));
            nameField.setText(wp.getName());
            categoryField.setText(wp.getCategory());
            priceField.setText(String.valueOf(wp.getPrice()));
            stockField.setText(String.valueOf(wp.getStock()));
            warrantyField.setText(String.valueOf(wp.getWarrantyMonths()));
        }
    }

    /** Показать диалог и дождаться результата. */
    public Optional<Product> showAndWait() {
        dialog.showAndWait();
        return Optional.ofNullable(result);
    }

    /** Обработчик OK: парсит поля, валидирует, создаёт объект. */
    private void onOk() {
        try {
            int article = Integer.parseInt(articleField.getText().trim());
            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            double price = Double.parseDouble(priceField.getText().trim());
            int stock = Integer.parseInt(stockField.getText().trim());
            int warranty = Integer.parseInt(warrantyField.getText().trim());

            WarrantyProduct product = new WarrantyProduct(article, name, category, price, stock, warranty);

            List<String> errors = product.validate();
            if (!errors.isEmpty()) {
                showError("Ошибки валидации:\n\n" + String.join("\n", errors));
                return; // не закрываем диалог
            }

            result = product;
            dialog.close();
        } catch (NumberFormatException ex) {
            showError("Неверный формат числа. Проверьте поля «Артикул», «Цена», «Остаток», «Гарантия».\n\n" +
                    "Сообщение: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialog);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}