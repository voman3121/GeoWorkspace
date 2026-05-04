package app;

import db.DBConnection;
//import db.DBServer;
import db.SchemaInitializer;
import ui.WorkspaceFrame;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        try {
//            DBServer.start();
            DBConnection.initialize();
            SchemaInitializer.initialize();

            SwingUtilities.invokeLater(() -> {
                WorkspaceFrame frame = new WorkspaceFrame();
                frame.setVisible(true);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}