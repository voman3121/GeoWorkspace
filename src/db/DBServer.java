package db;
import org.h2.tools.Server; import java.sql.SQLException;
public class DBServer {
    private static Server tcpServer;
    public static void start(){
        try{
            tcpServer=Server.createTcpServer("-tcp","-tcpAllowOthers","-ifNotExists","-tcpPort","9092").start();
            System.out.println("H2 TCP server started on port 9092");
        }catch(SQLException e){System.out.println("Port 9092 already in use — connecting to existing H2 server.");}
    }
    public static void stop(){if(tcpServer!=null)tcpServer.stop();}
}