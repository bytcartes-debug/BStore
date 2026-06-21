package ui;

import service.BarracaService;
import ui.panels.CategoriasPanel;
import ui.panels.DashboardPanel;
import ui.panels.ProdutosPanel;
import ui.panels.VendasPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainFrame extends JFrame {

    private BarracaService service;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    private DashboardPanel dashboardPanel;
    private CategoriasPanel categoriasPanel;
    private ProdutosPanel produtosPanel;
    private VendasPanel vendasPanel;

    private JButton btnActivo = null;

    public MainFrame() {
        service = new BarracaService();
        configurarJanela();
        construirUI();
        navegarPara("dashboard");
    }

    private void configurarJanela() {
        setTitle("Sistema de Gestão de Barraca — ISCIM 2026");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private void construirUI() {
        setLayout(new BorderLayout());

        add(criarSidebar(), BorderLayout.WEST);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(Cores.CINZENTO_FUNDO);

        dashboardPanel  = new DashboardPanel(service);
        categoriasPanel = new CategoriasPanel(service);
        produtosPanel   = new ProdutosPanel(service);
        vendasPanel     = new VendasPanel(service);

        contentPanel.add(dashboardPanel,  "dashboard");
        contentPanel.add(categoriasPanel, "categorias");
        contentPanel.add(produtosPanel,   "produtos");
        contentPanel.add(vendasPanel,     "vendas");

        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel criarSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Cores.VERDE_ESCURO);
        sidebar.setPreferredSize(new Dimension(220, 0));

        // Logo / título
        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(Cores.VERDE_ESCURO);
        logoPanel.setBorder(new EmptyBorder(25, 20, 20, 20));
        logoPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel lblLogo = new JLabel("🏪");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        JLabel lblTitulo = new JLabel("<html><b>Barraca</b><br><span style='font-size:10px'>Gestão Comercial</span></html>");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitulo.setForeground(Color.WHITE);

        logoPanel.add(lblLogo, BorderLayout.WEST);
        logoPanel.add(lblTitulo, BorderLayout.CENTER);
        sidebar.add(logoPanel);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 255, 255, 40));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(sep);
        sidebar.add(Box.createVerticalStrut(10));

        JButton btnDash   = criarBotaoNav("📊  Dashboard",  "dashboard");
        JButton btnCats   = criarBotaoNav("📁  Categorias", "categorias");
        JButton btnProds  = criarBotaoNav("📦  Produtos",   "produtos");
        JButton btnVendas = criarBotaoNav("💰  Vendas",     "vendas");

        sidebar.add(btnDash);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(btnCats);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(btnProds);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(btnVendas);

        sidebar.add(Box.createVerticalGlue());

        // Rodapé da sidebar
        JLabel lblRodape = new JLabel("<html><center>ISCIM — 3.º Ano ISD<br>Engenharia de Software<br>2026</center></html>");
        lblRodape.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRodape.setForeground(new Color(255, 255, 255, 100));
        lblRodape.setAlignmentX(CENTER_ALIGNMENT);
        lblRodape.setBorder(new EmptyBorder(0, 0, 20, 0));
        sidebar.add(lblRodape);

        btnActivo = btnDash;
        marcarActivo(btnDash);

        return sidebar;
    }

    private JButton criarBotaoNav(String texto, String pagina) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setForeground(new Color(200, 230, 215));
        btn.setBackground(Cores.VERDE_ESCURO);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btn.setBorder(new EmptyBorder(10, 22, 10, 22));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn != btnActivo) btn.setBackground(new Color(0, 80, 50));
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (btn != btnActivo) btn.setBackground(Cores.VERDE_ESCURO);
            }
        });

        btn.addActionListener(e -> {
            marcarActivo(btn);
            navegarPara(pagina);
        });

        return btn;
    }

    private void marcarActivo(JButton btn) {
        if (btnActivo != null) {
            btnActivo.setBackground(Cores.VERDE_ESCURO);
            btnActivo.setForeground(new Color(200, 230, 215));
            btnActivo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        }
        btn.setBackground(Cores.VERDE_PRINCIPAL);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnActivo = btn;
    }

    private void navegarPara(String pagina) {
        cardLayout.show(contentPanel, pagina);
        switch (pagina) {
            case "dashboard":  dashboardPanel.actualizar();  break;
            case "categorias": categoriasPanel.actualizar(); break;
            case "produtos":   produtosPanel.actualizar();   break;
            case "vendas":     vendasPanel.actualizar();     break;
        }
    }
}
