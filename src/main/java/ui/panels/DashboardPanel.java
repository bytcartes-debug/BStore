package ui.panels;

import service.BarracaService;
import model.Produto;
import ui.Cores;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

public class DashboardPanel extends JPanel {

    private BarracaService service;
    private JLabel lblTotalVendas, lblNumVendas, lblTotalProdutos, lblStockBaixo;
    private JPanel listaAlertasPanel;

    public DashboardPanel(BarracaService service) {
        this.service = service;
        setLayout(new BorderLayout(0, 20));
        setBackground(Cores.CINZENTO_FUNDO);
        setBorder(new EmptyBorder(25, 25, 25, 25));
        construirUI();
    }

    private void construirUI() {
        JLabel titulo = new JLabel("Dashboard — Resumo do Dia");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(Cores.TEXTO_ESCURO);
        titulo.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(titulo, BorderLayout.NORTH);

        JPanel cardsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        cardsPanel.setOpaque(false);

        lblTotalVendas  = criarCard(cardsPanel, "Vendas Hoje (MZN)", "0.00", Cores.VERDE_PRINCIPAL);
        lblNumVendas    = criarCard(cardsPanel, "Transacções Hoje", "0",    new Color(52, 152, 219));
        lblTotalProdutos= criarCard(cardsPanel, "Total de Produtos", "0",   new Color(155, 89, 182));
        lblStockBaixo   = criarCard(cardsPanel, "Alertas de Stock", "0",   Cores.LARANJA_ALERTA);

        add(cardsPanel, BorderLayout.CENTER);

        JPanel alertasOuter = new JPanel(new BorderLayout());
        alertasOuter.setOpaque(false);

        JLabel lblTituloAlertas = new JLabel("⚠  Produtos com Stock Baixo");
        lblTituloAlertas.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTituloAlertas.setForeground(Cores.LARANJA_ALERTA);
        lblTituloAlertas.setBorder(new EmptyBorder(0, 0, 8, 0));
        alertasOuter.add(lblTituloAlertas, BorderLayout.NORTH);

        listaAlertasPanel = new JPanel();
        listaAlertasPanel.setLayout(new BoxLayout(listaAlertasPanel, BoxLayout.Y_AXIS));
        listaAlertasPanel.setBackground(Cores.CINZENTO_CARD);
        listaAlertasPanel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Cores.BORDA, 1, true),
            new EmptyBorder(10, 15, 10, 15)
        ));

        JScrollPane scroll = new JScrollPane(listaAlertasPanel);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setBorder(null);
        alertasOuter.add(scroll, BorderLayout.CENTER);
        add(alertasOuter, BorderLayout.SOUTH);
    }

    private JLabel criarCard(JPanel pai, String titulo, String valorInicial, Color cor) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Cores.CINZENTO_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Cores.BORDA, 1, true),
            new EmptyBorder(18, 18, 18, 18)
        ));

        JPanel topBar = new JPanel();
        topBar.setPreferredSize(new Dimension(0, 4));
        topBar.setBackground(cor);
        card.add(topBar, BorderLayout.NORTH);

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitulo.setForeground(Cores.TEXTO_CINZENTO);
        card.add(lblTitulo, BorderLayout.CENTER);

        JLabel lblValor = new JLabel(valorInicial);
        lblValor.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblValor.setForeground(cor);
        card.add(lblValor, BorderLayout.SOUTH);

        pai.add(card);
        return lblValor;
    }

    public void actualizar() {
        double totalVendas = service.totalVendasHoje();
        long numVendas     = service.vendasHoje();
        long totalProdutos = service.totalProdutos();
        List<Produto> alertas = service.produtosComStockBaixo();

        lblTotalVendas.setText(String.format("%.2f", totalVendas));
        lblNumVendas.setText(String.valueOf(numVendas));
        lblTotalProdutos.setText(String.valueOf(totalProdutos));
        lblStockBaixo.setText(String.valueOf(alertas.size()));

        listaAlertasPanel.removeAll();

        if (alertas.isEmpty()) {
            JLabel ok = new JLabel("✓  Nenhum produto com stock em alerta.");
            ok.setForeground(Cores.VERDE_PRINCIPAL);
            ok.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            listaAlertasPanel.add(ok);
        } else {
            for (Produto p : alertas) {
                JPanel linha = new JPanel(new BorderLayout());
                linha.setOpaque(false);
                linha.setBorder(new EmptyBorder(4, 0, 4, 0));

                JLabel nome = new JLabel("⚠  " + p.getNome());
                nome.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                nome.setForeground(Cores.TEXTO_ESCURO);

                JLabel stock = new JLabel(p.getQuantidadeStock() + " " + p.getUnidade() + " restantes");
                stock.setFont(new Font("Segoe UI", Font.BOLD, 13));
                stock.setForeground(p.getQuantidadeStock() == 0 ? Cores.VERMELHO : Cores.LARANJA_ALERTA);

                linha.add(nome, BorderLayout.WEST);
                linha.add(stock, BorderLayout.EAST);

                listaAlertasPanel.add(linha);
                listaAlertasPanel.add(new JSeparator());
            }
        }

        listaAlertasPanel.revalidate();
        listaAlertasPanel.repaint();
    }
}
