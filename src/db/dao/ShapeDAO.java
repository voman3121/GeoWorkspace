package db.dao;

import db.DBConnection;
import model.Shape;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ShapeDAO {

    public Shape insert(Shape shape) throws Exception {
        String sql="INSERT INTO shapes (label,shape_type,node_ids,area,perimeter,extra_data) VALUES (?,?,?,?,?,?)";
        try(Connection conn=DBConnection.getConnection();
            PreparedStatement ps=conn.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            ps.setString(1,shape.getLabel());ps.setString(2,shape.getShapeType());
            ps.setString(3,Shape.encodeIds(shape.getNodeIds()));
            ps.setDouble(4,shape.getArea());ps.setDouble(5,shape.getPerimeter());
            ps.setString(6,shape.getExtraData());
            ps.executeUpdate();
            ResultSet rs=ps.getGeneratedKeys();if(rs.next())shape.setId(rs.getLong(1));
        }
        System.out.println("[DB] Shape id="+shape.getId()+" type="+shape.getShapeType());
        return shape;
    }

    public List<Shape> getAll() throws Exception {
        List<Shape> list=new ArrayList<>();
        try(Connection conn=DBConnection.getConnection();Statement st=conn.createStatement();
            ResultSet rs=st.executeQuery("SELECT * FROM shapes")){
            while(rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void delete(long id) throws Exception {
        try(Connection conn=DBConnection.getConnection();
            PreparedStatement ps=conn.prepareStatement("DELETE FROM shapes WHERE id=?")){
            ps.setLong(1,id);ps.executeUpdate();
        }
    }

    private Shape map(ResultSet rs) throws SQLException {
        return new Shape(rs.getLong("id"),rs.getString("label"),rs.getString("shape_type"),
                Shape.decodeIds(rs.getString("node_ids")),
                rs.getDouble("area"),rs.getDouble("perimeter"),
                rs.getString("extra_data"));
    }
}