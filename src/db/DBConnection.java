package db;
import java.sql.*; 
public class DBConnection {
    private static final String URL="jdbc:h2:tcp://localhost:9092/~/geodb";
    private static final String USER="sa",PASSWORD="";
    public static void initialize()throws ClassNotFoundException{Class.forName("org.h2.Driver");}
    public static Connection getConnection()throws SQLException{return DriverManager.getConnection(URL,USER,PASSWORD);}
}