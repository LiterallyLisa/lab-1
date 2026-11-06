package org.example.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.example.structure.BTreeNode;
import org.example.structure.BTreeStore;

import java.util.Set;

/**
 * Панель для визуализации B-дерева.
 * Рисует узлы (прямоугольники с ключами) и связи между ними.
 *
 * Раскладка считается по ширине поддеревьев, поэтому узлы не налезают друг на друга:
 * родитель стоит по центру над своими детьми, а линия к i-му ребёнку выходит из границы между ключами.
 * Если дерево слишком большое, чтобы поместиться в холст, рисуются только верхние уровни.
 * Посещённые при поиске/вставке узлы подсвечиваются.
 */
public class TreeCanvas {

    /** Размеры. */
    private static final double NODE_WIDTH = 56;
    private static final double NODE_HEIGHT = 28;
    private static final double LEVEL_HEIGHT = 70;
    private static final double HORIZONTAL_GAP = 16;
    private static final double PADDING = 20;

    /** Максимальная ширина холста: у JavaFX-текстур есть аппаратный предел. */
    private static final double MAX_CANVAS_WIDTH = 8000;

    private static final Color NORMAL_FILL = Color.LIGHTBLUE;
    private static final Color NORMAL_STROKE = Color.DARKBLUE;
    private static final Color HIGHLIGHT_FILL = Color.GOLD;
    private static final Color HIGHLIGHT_STROKE = Color.DARKORANGE;

    private final Canvas canvas;
    private final double minWidth;
    private final double minHeight;

    public TreeCanvas(double width, double height) {
        this.canvas = new Canvas(width, height);
        this.minWidth = width;
        this.minHeight = height;
    }

    /** Возвращает Canvas — чтобы встроить в layout (лучше внутри ScrollPane). */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Рисует дерево.
     *
     * @param tree        дерево для отрисовки
     * @param highlighted узлы, которые нужно подсветить (может быть пустым)
     * @return пояснение для пользователя (например, что показаны не все уровни); пустая строка, если нечего сказать
     */
    public String draw(BTreeStore<?, ?> tree, Set<? extends BTreeNode<?, ?>> highlighted) {
        GraphicsContext g = canvas.getGraphicsContext2D();

        if (tree == null || tree.size() == 0) {
            canvas.setWidth(minWidth);
            canvas.setHeight(minHeight);
            g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g.setFill(Color.GRAY);
            g.setFont(Font.font(14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("Дерево пустое", canvas.getWidth() / 2, canvas.getHeight() / 2);
            return "";
        }

        BTreeNode<?, ?> root = tree.getRoot();
        int height = tree.getStats().getHeight();

        // Выбираем, сколько верхних уровней помещается в холст
        int levels = 1;
        double layoutWidth = subtreeWidth(root, 1, levels);
        while (levels < height) {
            double w = subtreeWidth(root, 1, levels + 1);
            if (w + 2 * PADDING > MAX_CANVAS_WIDTH) {
                break;
            }
            levels++;
            layoutWidth = w;
        }

        canvas.setWidth(Math.max(minWidth, layoutWidth + 2 * PADDING));
        canvas.setHeight(Math.max(minHeight, levels * LEVEL_HEIGHT + 2 * PADDING));
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double left = (canvas.getWidth() - layoutWidth) / 2;
        drawNode(g, root, 1, levels, left, highlighted);

        return levels < height
                ? "Дерево слишком большое: показаны верхние " + levels + " из " + height + " уровней."
                : "";
    }

    /**
     * Ширина, которую занимает поддерево, если рисовать не глубже maxLevel уровней.
     */
    private double subtreeWidth(BTreeNode<?, ?> node, int level, int maxLevel) {
        double own = node.getKeyCount() * NODE_WIDTH;
        if (node.isLeaf() || level >= maxLevel) {
            return own;
        }
        double children = HORIZONTAL_GAP * node.getKeyCount();
        for (int i = 0; i <= node.getKeyCount(); i++) {
            children += subtreeWidth(node.getChild(i), level + 1, maxLevel);
        }
        return Math.max(own, children);
    }

    /**
     * Рисует узел и рекурсивно его детей.
     *
     * @param left левая граница полосы, выделенной под это поддерево
     * @return x-координата центра нарисованного узла
     */
    private double drawNode(GraphicsContext g, BTreeNode<?, ?> node, int level, int maxLevel,
                            double left, Set<? extends BTreeNode<?, ?>> highlighted) {
        int keyCount = node.getKeyCount();
        double own = keyCount * NODE_WIDTH;
        double y = PADDING + (level - 1) * LEVEL_HEIGHT;
        boolean expanded = !node.isLeaf() && level < maxLevel;

        double center;
        double[] childCenters = null;

        if (expanded) {
            double total = subtreeWidth(node, level, maxLevel);
            double[] widths = new double[keyCount + 1];
            double childrenTotal = HORIZONTAL_GAP * keyCount;
            for (int i = 0; i <= keyCount; i++) {
                widths[i] = subtreeWidth(node.getChild(i), level + 1, maxLevel);
                childrenTotal += widths[i];
            }
            double childLeft = left + (total - childrenTotal) / 2;
            childCenters = new double[keyCount + 1];
            for (int i = 0; i <= keyCount; i++) {
                childCenters[i] = drawNode(g, node.getChild(i), level + 1, maxLevel, childLeft, highlighted);
                childLeft += widths[i] + HORIZONTAL_GAP;
            }
            center = (childCenters[0] + childCenters[keyCount]) / 2;
        } else {
            center = left + own / 2;
        }

        double nodeX = center - own / 2;

        // Линии к детям: i-й ребёнок выходит из i-й границы между ключами
        if (expanded) {
            g.setStroke(Color.GRAY);
            g.setLineWidth(1);
            for (int i = 0; i <= keyCount; i++) {
                g.strokeLine(nodeX + i * NODE_WIDTH, y + NODE_HEIGHT, childCenters[i], y + LEVEL_HEIGHT);
            }
        }

        // Прямоугольник узла
        boolean isHighlighted = highlighted.contains(node);
        g.setFill(isHighlighted ? HIGHLIGHT_FILL : NORMAL_FILL);
        g.setStroke(isHighlighted ? HIGHLIGHT_STROKE : NORMAL_STROKE);
        g.setLineWidth(2);
        g.fillRect(nodeX, y, own, NODE_HEIGHT);
        g.strokeRect(nodeX, y, own, NODE_HEIGHT);

        // Ключи
        g.setFill(Color.BLACK);
        g.setFont(Font.font(12));
        g.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < keyCount; i++) {
            double cellX = nodeX + i * NODE_WIDTH;
            if (i > 0) {
                g.strokeLine(cellX, y, cellX, y + NODE_HEIGHT);
            }
            g.fillText(String.valueOf(node.getKey(i)), cellX + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4);
        }

        // У обрезанного узла есть скрытые потомки — пометим
        if (!node.isLeaf() && !expanded) {
            g.setFill(Color.GRAY);
            g.fillText("...", center, y + NODE_HEIGHT + 14);
        }

        return center;
    }
}
