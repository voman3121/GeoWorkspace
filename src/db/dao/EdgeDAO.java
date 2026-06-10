package db.dao;

import db.DBConnection;
import model.Edge;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EdgeDAO {

    public Edge insert(Edge edge) throws Exception {
        String sql = "INSERT INTO edges (node_a_id, node_b_id, length) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, edge.getNodeAId());
            ps.setLong(2, edge.getNodeBId());
            ps.setDouble(3, edge.getLength());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) edge.setId(rs.getLong(1));
        }
        System.out.println("[DB] Edge inserted id=" + edge.getId()
            + " (" + edge.getNodeAId() + "<->" + edge.getNodeBId()
            + ") len=" + String.format("%.1f", edge.getLength()));
        return edge;
    }

    public List<Edge> getAll() throws Exception {
        List<Edge> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM edges")) {
            while (rs.next())
                list.add(new Edge(rs.getLong("id"), rs.getLong("node_a_id"),
                                  rs.getLong("node_b_id"), rs.getDouble("length")));
        }
        return list;
    }

    public void delete(long nodeAId, long nodeBId) throws Exception {
        String sql = "DELETE FROM edges WHERE (node_a_id=? AND node_b_id=?) OR (node_a_id=? AND node_b_id=?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nodeAId); ps.setLong(2, nodeBId);
            ps.setLong(3, nodeBId); ps.setLong(4, nodeAId);
            int rows = ps.executeUpdate();
            System.out.println("[DB] Edge deleted between " + nodeAId + " and " + nodeBId + " (rows=" + rows + ")");
        }
    }
}