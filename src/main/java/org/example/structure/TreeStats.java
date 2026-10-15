package org.example.structure;

/**
 * Статистика B-дерева.
 * Immutable — после создания не меняется.
 */
public class TreeStats {
    private final int totalNodes;
    private final int leafNodes;
    private final int internalNodes;
    private final int height;
    private final double avgKeysPerNode;
    private final int fullNodes;
    private final int notFullNodes;
    private final int totalKeys;

    public TreeStats(int totalNodes, int leafNodes, int internalNodes, int height,
                     double avgKeysPerNode, int fullNodes, int notFullNodes, int totalKeys) {
        this.totalNodes = totalNodes;
        this.leafNodes = leafNodes;
        this.internalNodes = internalNodes;
        this.height = height;
        this.avgKeysPerNode = avgKeysPerNode;
        this.fullNodes = fullNodes;
        this.notFullNodes = notFullNodes;
        this.totalKeys = totalKeys;
    }

    public int getTotalNodes() { return totalNodes; }
    public int getLeafNodes() { return leafNodes; }
    public int getInternalNodes() { return internalNodes; }
    public int getHeight() { return height; }
    public double getAvgKeysPerNode() { return avgKeysPerNode; }
    public int getFullNodes() { return fullNodes; }
    public int getNotFullNodes() { return notFullNodes; }
    public int getTotalKeys() { return totalKeys; }

    @Override
    public String toString() {
        return "TreeStats{" +
                "totalNodes=" + totalNodes +
                ", leafNodes=" + leafNodes +
                ", internalNodes=" + internalNodes +
                ", height=" + height +
                ", avgKeysPerNode=" + String.format("%.2f", avgKeysPerNode) +
                ", fullNodes=" + fullNodes +
                ", notFullNodes=" + notFullNodes +
                ", totalKeys=" + totalKeys +
                '}';
    }
}