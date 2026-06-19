package app;

import db.DBServer;
import db.DBConnection;
import db.SchemaInitializer;
import ui.WorkspaceFrame;

import org.opencv.core.Core;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            // Load OpenCV native library once at startup.
            // Requires -Djava.library.path=lib pointing at opencv_java*.dll
            try {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
                System.out.println("[OpenCV] Native library loaded: " + Core.VERSION);
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[OpenCV] WARNING: native library not found on java.library.path.");
                System.err.println("[OpenCV] Image import will fail until opencv_java*.dll is in lib/ and");
                System.err.println("[OpenCV] you launch with -Djava.library.path=lib");
            }

            DBServer.start();
            DBConnection.initialize();
            SchemaInitializer.initialize();
            SwingUtilities.invokeLater(() -> new WorkspaceFrame().setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}