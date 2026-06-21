package ui.panels;

import model.Produto;
import model.Venda;
import service.BarracaService;
import ui.Cores;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class VendasPanel extends JPanel {

    private BarracaService service;
    private DefaultTableModel tableModel;
    private JTable tabela;
    private JComboBox<Produto> cboProduto;
    private JTextField txtQuantidade, txtObservacao;
    private JLabel lblPrecoUnit, lblTotalVenda;
    private JTextField txtDataInicio, txtDataFim;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public VendasPanel(BarracaService service) {
        this.service = service;
        setLayout(new BorderLayout(15, 0));
        setBackground(Cores.CINZENTO_FUNDO);
        setBorder(new EmptyBorder(25, 25, 25, 25));
        construirUI();
    }

    private void construirUI() {
        JLabel titulo = new JLabel("Registo de Vendas");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(Cores.TEXTO_ESCURO);
        titulo.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(titulo, BorderLayout.WEST);
        add(topo, BorderLayout.NORTH);

        add(criarFormularioVenda(), BorderLayout.WEST);
        add(criarHistorico(), BorderLayout.CENTER);
    }

    private JPanel criarFormularioVenda() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Cores.CINZENTO_CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Cores.BORDA, 1, true),
            new EmptyBorder(20, 20, 20, 20)
        ));
        form.setPreferredSize(new Dimension(300, 0));

        JLabel lblForm = new JLabel("Nova Venda");
        lblForm.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblForm.setForeground(Cores.TEXTO_ESCURO);
        lblForm.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lblForm);
        form.add(Box.createVerticalStrut(18));

        form.add(lbl("Produto *"));
        cboProduto = new JComboBox<>();
        cboProduto.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cboProduto.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cboProduto.setAlignmentX(LEFT_ALIGNMENT);
        cboProduto.addActionListener(e -> actualizarPreco());
        form.add(cboProduto);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Quantidade *"));
        txtQuantidade = campo();
        txtQuantidade.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { calcularTotal(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { calcularTotal(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { calcularTotal(); }
        });
        form.add(txtQuantidade);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Preço unitário"));
        lblPrecoUnit = new JLabel("0.00 MZN");
        lblPrecoUnit.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblPrecoUnit.setForeground(Cores.TEXTO_CINZENTO);
        lblPrecoUnit.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lblPrecoUnit);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Observação (opcional)"));
        txtObservacao = campo();
        form.add(txtObservacao);
        form.add(Box.createVerticalStrut(15));

        JPanel totalPanel = new JPanel(new BorderLayout());
        totalPanel.setOpaque(false);
        totalPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        totalPanel.setAlignmentX(LEFT_ALIGNMENT);
        totalPanel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Cores.VERDE_PRINCIPAL, 1, true),
            new EmptyBorder(10, 12, 10, 12)
        ));
        JLabel lblTotalLabel = new JLabel("TOTAL");
        lblTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTotalLabel.setForeground(Cores.TEXTO_CINZENTO);
        lblTotalVenda = new JLabel("0.00 MZN");
        lblTotalVenda.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTotalVenda.setForeground(Cores.VERDE_PRINCIPAL);
        totalPanel.add(lblTotalLabel, BorderLayout.NORTH);
        totalPanel.add(lblTotalVenda, BorderLayout.CENTER);
        form.add(totalPanel);
        form.add(Box.createVerticalStrut(20));

        JButton btnRegistar = botao("  Registar Venda", Cores.VERDE_PRINCIPAL, Color.WHITE);
        btnRegistar.addActionListener(e -> registarVenda());
        form.add(btnRegistar);

        return form;
    }

    private JPanel criarHistorico() {
        JPanel painel = new JPanel(new BorderLayout(0, 10));
        painel.setOpaque(false);

        JPanel filtroPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filtroPanel.setOpaque(false);

        JLabel lblInicio = new JLabel("De:");
        lblInicio.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtDataInicio = new JTextField(LocalDate.now().withDayOfMonth(1).format(FMT), 10);
        txtDataInicio.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel lblFim = new JLabel("Até:");
        lblFim.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtDataFim = new JTextField(LocalDate.now().format(FMT), 10);
        txtDataFim.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton btnFiltrar = botao("Filtrar", new Color(52, 152, 219), Color.WHITE);
        btnFiltrar.setPreferredSize(new Dimension(90, 32));
        btnFiltrar.addActionListener(e -> filtrar());

        JButton btnHoje = botao("Só Hoje", Cores.VERDE_PRINCIPAL, Color.WHITE);
        btnHoje.setPreferredSize(new Dimension(90, 32));
        btnHoje.addActionListener(e -> {
            txtDataInicio.setText(LocalDate.now().format(FMT));
            txtDataFim.setText(LocalDate.now().format(FMT));
            filtrar();
        });

        filtroPanel.add(lblInicio);
        filtroPanel.add(txtDataInicio);
        filtroPanel.add(lblFim);
        filtroPanel.add(txtDataFim);
        filtroPanel.add(btnFiltrar);
        filtroPanel.add(btnHoje);

        painel.add(filtroPanel, BorderLayout.NORTH);

        String[] cols = {"ID", "Data", "Produto", "Qtd", "Preço Unit.", "Total (MZN)", "Obs."};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tabela = new JTable(tableModel);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.setRowHeight(32);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabela.getTableHeader().setBackground(Cores.VERDE_CLARO);
        tabela.setSelectionBackground(Cores.VERDE_CLARO);
        tabela.setGridColor(Cores.BORDA);
        tabela.getColumnModel().getColumn(0).setMaxWidth(50);
        this.tabela = tabela;

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(new LineBorder(Cores.BORDA, 1, true));
        painel.add(scroll, BorderLayout.CENTER);

        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rodape.setOpaque(false);
        JLabel lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTotal.setForeground(Cores.VERDE_PRINCIPAL);
        rodape.add(lblTotal);

        tableModel.addTableModelListener(e -> {
            double soma = 0;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                try { soma += Double.parseDouble(tableModel.getValueAt(i, 5).toString()); } catch (Exception ex) {}
            }
            lblTotal.setText(String.format("Total período: %.2f MZN  |  %d transacções",
                soma, tableModel.getRowCount()));
        });
        painel.add(rodape, BorderLayout.SOUTH);

        return painel;
    }

    private JLabel lbl(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(Cores.TEXTO_CINZENTO);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JTextField campo() {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        f.setAlignmentX(LEFT_ALIGNMENT);
        return f;
    }

    private JButton botao(String t, Color bg, Color fg) {
        JButton b = new JButton(t);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void actualizarPreco() {
        Produto p = (Produto) cboProduto.getSelectedItem();
        if (p != null) {
            lblPrecoUnit.setText(String.format("%.2f MZN  |  Stock: %d %s",
                p.getPreco(), p.getQuantidadeStock(), p.getUnidade() != null ? p.getUnidade() : ""));
        }
        calcularTotal();
    }

    private void calcularTotal() {
        Produto p = (Produto) cboProduto.getSelectedItem();
        if (p == null) return;
        try {
            int qtd = Integer.parseInt(txtQuantidade.getText().trim());
            double total = qtd * p.getPreco();
            lblTotalVenda.setText(String.format("%.2f MZN", total));
        } catch (NumberFormatException e) {
            lblTotalVenda.setText("0.00 MZN");
        }
    }

    private void registarVenda() {
        Produto p = (Produto) cboProduto.getSelectedItem();
        if (p == null) { JOptionPane.showMessageDialog(this, "Seleccione um produto."); return; }
        try {
            int qtd = Integer.parseInt(txtQuantidade.getText().trim());
            String obs = txtObservacao.getText().trim();
            Venda v = service.registarVenda(p.getId(), qtd, obs.isEmpty() ? null : obs);
            JOptionPane.showMessageDialog(this,
                String.format("Venda registada!\nTotal: %.2f MZN", v.getTotal()),
                "Venda registada", JOptionPane.INFORMATION_MESSAGE);
            txtQuantidade.setText("");
            txtObservacao.setText("");
            actualizar();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "A quantidade deve ser um número inteiro.", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void filtrar() {
        try {
            LocalDate ini = LocalDate.parse(txtDataInicio.getText().trim(), FMT);
            LocalDate fim = LocalDate.parse(txtDataFim.getText().trim(), FMT);
            popularTabela(service.vendasEntreDatas(ini, fim));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Formato de data inválido. Use dd/MM/yyyy", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void popularTabela(List<Venda> lista) {
        tableModel.setRowCount(0);
        for (Venda v : lista) {
            tableModel.addRow(new Object[]{
                v.getId(),
                v.getDataVenda().format(FMT),
                v.getProduto().getNome(),
                v.getQuantidade(),
                String.format("%.2f", v.getPrecoUnitario()),
                String.format("%.2f", v.getTotal()),
                v.getObservacao() != null ? v.getObservacao() : ""
            });
        }
    }

    public void actualizar() {
        cboProduto.removeAllItems();
        for (Produto p : service.listarProdutos()) cboProduto.addItem(p);
        actualizarPreco();
        filtrar();
    }
}
