package model;

public class Edge {
    private long id;
    private long nodeAId;
    private long nodeBId;
    private double length;

    public Edge(long id, long nodeAId, long nodeBId, double length) {
        this.id = id;
        this.nodeAId = nodeAId;
        this.nodeBId = nodeBId;
        this.length = length;
    }

    public Edge(long nodeAId, long nodeBId, double length) {
        this(0, nodeAId, nodeBId, length);
    }

    public long getId() { return id; }
    public long getNodeAId() { return nodeAId; }
    public long getNodeBId() { return nodeBId; }
    public double getLength() { return length; }

    public void setId(long id) { this.id = id; }
}