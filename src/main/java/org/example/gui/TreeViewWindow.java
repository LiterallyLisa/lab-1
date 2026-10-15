package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.example.structure.BTreeStore;
import org.example.model.Product;

/**
 * Отдельное окно для визуализации B-дерева.
 */
public class TreeViewWindow {

    private final Stage window;
    private final TreeCanvas treeCanvas;
    private final BTreeStore<Integer, Product> tree;

    public TreeViewWindow(Stage owner, BTreeStore<Integer, Product> tree) {
        this.tree = tree;
        this.window = new Stage();
        this.window.initOwner(owner);
        this.window.setTitle("Визуализация B-дерева");

        // Canvas
        this.treeCanvas = new TreeCanvas(900, 600);

        // Кнопка «Обновить»
        Button refreshBtn = new Button("Обновить");
        refreshBtn.setOnAction(e -> treeCanvas.draw(tree));

        // Кнопка «Закрыть»
        Button closeBtn = new Button("Закрыть");
        closeBtn.setOnAction(e -> window.close());

        HBox buttons = new HBox(10, refreshBtn, closeBtn);
        buttons.setPadding(new Insets(10));

        Label info = new Label("Элементов в дереве: " + tree.size());
        info.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(treeCanvas.getCanvas());
        root.setTop(info);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 920, 680);
        window.setScene(scene);
    }

    /** Показать окно и сразу нарисовать дерево. */
    public void show() {
        treeCanvas.draw(tree);   // рисуем сразу
        window.show();
    }
}