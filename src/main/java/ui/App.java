package ui;

import util.JPAUtil;

import javax.swing.*;

public class App {

    public static void main(String[] args) {
        // Aplica o look and feel FlatLaf (moderno) se disponível
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            // fallback para o sistema operativo
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ex) { /* usa o padrão */ }
        }

        // Inicializa JPA antes de abrir a UI
        JPAUtil.inicializar();

        // Abre a janela principal na thread de eventos do Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });

        // Fecha o JPA quando a JVM terminar
        Runtime.getRuntime().addShutdownHook(new Thread(JPAUtil::fechar));
    }
}
