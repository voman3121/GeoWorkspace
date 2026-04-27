package model;

public class Edge {
    private long id;
    private long nodeAId;
    private long nodeBId;

    public Edge(long id, long nodeAId, long nodeBId) {
        this.id = id;
        this.nodeAId = nodeAId;
        this.nodeBId = nodeBId;
    }

    public Edge(long nodeAId, long nodeBId) {
        this(0, nodeAId, nodeBId);
    }

    public long getId() { return id; }
    public long getNodeAId() { return nodeAId; }
    public long getNodeBId() { return nodeBId; }

    public void setId(long id) { this.id = id; }
}