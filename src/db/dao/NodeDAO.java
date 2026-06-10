package db.dao;

import db.DBConnection;
import model.Node;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NodeDAO {

    public Node insert(Node node) throws Exception {
        String sql = "INSERT INTO nodes (x, y, label) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDouble(1, node.getX());
            ps.setDouble(2, node.getY());
            ps.setString(3, node.getLabel());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) node.setId(rs.getLong(1));
        }
        System.out.println("[DB] Node inserted id=" + node.getId() + " label=" + node.getLabel());
        return node;
    }

    public List<Node> getAll() throws Exception {
        List<Node> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM nodes")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Node findById(long id) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM nodes WHERE id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        }
    }

    /**
     * Delete a node: clears its id from any other node's adj columns,
     * then deletes the row. Edges cascade via FK ON DELETE CASCADE.
     */
    public void delete(long id) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            // Clear this node from all adjacency slots in other rows
            String clearSql = """
                UPDATE nodes SET
                  adj1 = CASE WHEN adj1 = ? THEN NULL ELSE adj1 END,
                  adj2 = CASE WHEN adj2 = ? THEN NULL ELSE adj2 END,
                  adj3 = CASE WHEN adj3 = ? THEN NULL ELSE adj3 END,
                  adj4 = CASE WHEN adj4 = ? THEN NULL ELSE adj4 END
                WHERE adj1 = ? OR adj2 = ? OR adj3 = ? OR adj4 = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(clearSql)) {
                for (int i = 1; i <= 8; i++) ps.setLong(i, id);
                ps.executeUpdate();
            }
            // Delete the node (edges cascade)
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM nodes WHERE id=?")) {
                ps.setLong(1, id);
                int rows = ps.executeUpdate();
                System.out.println("[DB] Node deleted id=" + id + " (rows=" + rows + ")");
            }
        }
    }

    public void addAdjacency(long aId, long bId) throws Exception {
        Node a = findById(aId);
        Node b = findById(bId);
        if (a == null || b == null) throw new Exception("Node not found");
        if (a.hasNeighbour(bId))   throw new Exception("Edge already exists between these nodes");
        if (a.firstFreeSlot() < 0) throw new Exception("Node " + a.getLabel() + " already has 4 neighbours");
        if (b.firstFreeSlot() < 0) throw new Exception("Node " + b.getLabel() + " already has 4 neighbours");

        try (Connection conn = DBConnection.getConnection()) {
            setAdj(conn, aId, a.firstFreeSlot(), bId);
            setAdj(conn, bId, b.firstFreeSlot(), aId);
        }
    }

    public void removeAdjacency(long aId, long bId) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            clearAdj(conn, aId, bId);
            clearAdj(conn, bId, aId);
        }
        System.out.println("[DB] Adjacency removed: " + aId + " <-> " + bId);
    }

    private void setAdj(Connection conn, long nodeId, int slot, long value) throws SQLException {
        String col = "adj" + slot;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE nodes SET " + col + " = ? WHERE id = ?")) {
            ps.setLong(1, value);
            ps.setLong(2, nodeId);
            ps.executeUpdate();
        }
    }

    private void clearAdj(Connection conn, long nodeId, long targetId) throws SQLException {
        String sql = """
            UPDATE nodes SET
              adj1 = CASE WHEN adj1 = ? THEN NULL ELSE adj1 END,
              adj2 = CASE WHEN adj2 = ? THEN NULL ELSE adj2 END,
              adj3 = CASE WHEN adj3 = ? THEN NULL ELSE adj3 END,
              adj4 = CASE WHEN adj4 = ? THEN NULL ELSE adj4 END
            WHERE id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, targetId); ps.setLong(2, targetId);
            ps.setLong(3, targetId); ps.setLong(4, targetId);
            ps.setLong(5, nodeId);
            ps.executeUpdate();
        }
    }

    private Node map(ResultSet rs) throws SQLException {
        return new Node(
            rs.getLong("id"), rs.getDouble("x"), rs.getDouble("y"), rs.getString("label"),
            rs.getLong("adj1"), rs.getLong("adj2"), rs.getLong("adj3"), rs.getLong("adj4")
        );
    }
}