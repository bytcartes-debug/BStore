package ui.panels;

import model.Categoria;
import model.Produto;
import service.BarracaService;
import ui.Cores;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class ProdutosPanel extends JPanel {

    private BarracaService service;
    private DefaultTableModel tableModel;
    private JTable tabela;
    private JTextField txtNome, txtPreco, txtStock, txtUnidade, txtStockMin, txtPesquisa;
    private JComboBox<Categoria> cboCategoria;
    private JButton btnSalvar, btnLimpar, btnEliminar;
    private Long idEmEdicao = null;

    public ProdutosPanel(BarracaService service) {
        this.service = service;
        setLayout(new BorderLayout(15, 0));
        setBackground(Cores.CINZENTO_FUNDO);
        setBorder(new EmptyBorder(25, 25, 25, 25));
        construirUI();
    }

    private void construirUI() {
        JLabel titulo = new JLabel("Gestão de Produtos");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(Cores.TEXTO_ESCURO);
        titulo.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel topoPanel = new JPanel(new BorderLayout());
        topoPanel.setOpaque(false);
        topoPanel.add(titulo, BorderLayout.WEST);
        add(topoPanel, BorderLayout.NORTH);

        add(criarFormulario(), BorderLayout.WEST);
        add(criarPainelTabela(), BorderLayout.CENTER);
    }

    private JPanel criarFormulario() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Cores.CINZENTO_CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Cores.BORDA, 1, true),
            new EmptyBorder(20, 20, 20, 20)
        ));
        form.setPreferredSize(new Dimension(300, 0));

        JLabel lblForm = new JLabel("Dados do Produto");
        lblForm.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblForm.setForeground(Cores.TEXTO_ESCURO);
        lblForm.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lblForm);
        form.add(Box.createVerticalStrut(18));

        form.add(lbl("Nome *")); txtNome = campo(); form.add(txtNome);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Preço (MZN) *")); txtPreco = campo(); form.add(txtPreco);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Stock actual *")); txtStock = campo(); form.add(txtStock);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Unidade (ex: kg, unidade)")); txtUnidade = campo(); form.add(txtUnidade);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Stock mínimo (alerta)")); txtStockMin = campo(); form.add(txtStockMin);
        form.add(Box.createVerticalStrut(10));

        form.add(lbl("Categoria"));
        cboCategoria = new JComboBox<>();
        cboCategoria.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cboCategoria.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        cboCategoria.setAlignmentX(LEFT_ALIGNMENT);
        form.add(cboCategoria);
        form.add(Box.createVerticalStrut(20));

        btnSalvar = botao("Guardar", Cores.VERDE_PRINCIPAL, Color.WHITE);
        btnSalvar.addActionListener(e -> salvar());
        form.add(btnSalvar);
        form.add(Box.createVerticalStrut(8));

        btnLimpar = botao("Limpar", Cores.CINZENTO_FUNDO, Cores.TEXTO_ESCURO);
        btnLimpar.addActionListener(e -> limpar());
        form.add(btnLimpar);
        form.add(Box.createVerticalStrut(8));

        btnEliminar = botao("Eliminar", Cores.VERMELHO, Color.WHITE);
        btnEliminar.setEnabled(false);
        btnEliminar.addActionListener(e -> eliminar());
        form.add(btnEliminar);

        return form;
    }

    private JPanel criarPainelTabela() {
        JPanel painel = new JPanel(new BorderLayout(0, 10));
        painel.setOpaque(false);

        JPanel barra = new JPanel(new BorderLayout(8, 0));
        barra.setOpaque(false);
        txtPesquisa = new JTextField();
        txtPesquisa.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtPesquisa.putClientProperty("JTextField.placeholderText", "Pesquisar produto...");
        txtPesquisa.addActionListener(e -> pesquisar());
        JButton btnPesq = botao("Pesquisar", new Color(52, 152, 219), Color.WHITE);
        btnPesq.setPreferredSize(new Dimension(100, 35));
        btnPesq.addActionListener(e -> pesquisar());
        barra.add(txtPesquisa, BorderLayout.CENTER);
        barra.add(btnPesq, BorderLayout.EAST);
        painel.add(barra, BorderLayout.NORTH);

        String[] cols = {"ID", "Nome", "Preço (MZN)", "Stock", "Unidade", "Categoria", "Alerta"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tabela = new JTable(tableModel);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.setRowHeight(32);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabela.getTableHeader().setBackground(Cores.VERDE_CLARO);
        tabela.setSelectionBackground(Cores.VERDE_CLARO);
        tabela.setGridColor(Cores.BORDA);
        tabela.getColumnModel().getColumn(0).setMaxWidth(50);
        tabela.getColumnModel().getColumn(6).setMaxWidth(70);

        tabela.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                Object alerta = t.getModel().getValueAt(row, 6);
                if (!sel && "⚠".equals(alerta)) {
                    c.setBackground(new Color(255, 243, 220));
                } else if (!sel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 252));
                }
                return c;
            }
        });

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selecionarLinha();
        });

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(new LineBorder(Cores.BORDA, 1, true));
        painel.add(scroll, BorderLayout.CENTER);

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

    private void salvar() {
        try {
            String nome     = txtNome.getText().trim();
            double preco    = Double.parseDouble(txtPreco.getText().trim());
            int stock       = Integer.parseInt(txtStock.getText().trim());
            String unidade  = txtUnidade.getText().trim();
            String smTxt    = txtStockMin.getText().trim();
            Integer stockMin = smTxt.isEmpty() ? 5 : Integer.parseInt(smTxt);
            Categoria cat   = (Categoria) cboCategoria.getSelectedItem();

            if (idEmEdicao == null) {
                service.criarProduto(nome, preco, stock, unidade, stockMin, cat);
                JOptionPane.showMessageDialog(this, "Produto criado com sucesso!");
            } else {
                Produto p = new dao.ProdutoDAO().buscarPorId(idEmEdicao);
                p.setNome(nome); p.setPreco(preco); p.setQuantidadeStock(stock);
                p.setUnidade(unidade); p.setStockMinimo(stockMin); p.setCategoria(cat);
                service.actualizarProduto(p);
                JOptionPane.showMessageDialog(this, "Produto actualizado!");
            }
            limpar();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Preço e Stock devem ser valores numéricos.", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminar() {
        if (idEmEdicao == null) return;
        int op = JOptionPane.showConfirmDialog(this, "Eliminar este produto?",
            "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (op == JOptionPane.YES_OPTION) {
            try {
                service.eliminarProduto(idEmEdicao);
                limpar();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void selecionarLinha() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) return;
        idEmEdicao = Long.parseLong(tableModel.getValueAt(linha, 0).toString());
        Produto p = new dao.ProdutoDAO().buscarPorId(idEmEdicao);
        if (p == null) return;
        txtNome.setText(p.getNome());
        txtPreco.setText(String.valueOf(p.getPreco()));
        txtStock.setText(String.valueOf(p.getQuantidadeStock()));
        txtUnidade.setText(p.getUnidade() != null ? p.getUnidade() : "");
        txtStockMin.setText(p.getStockMinimo() != null ? String.valueOf(p.getStockMinimo()) : "5");
        if (p.getCategoria() != null) {
            for (int i = 0; i < cboCategoria.getItemCount(); i++) {
                if (cboCategoria.getItemAt(i).getId().equals(p.getCategoria().getId())) {
                    cboCategoria.setSelectedIndex(i);
                    break;
                }
            }
        }
        btnEliminar.setEnabled(true);
        btnSalvar.setText("Actualizar");
    }

    private void limpar() {
        idEmEdicao = null;
        txtNome.setText(""); txtPreco.setText(""); txtStock.setText("");
        txtUnidade.setText(""); txtStockMin.setText(""); txtPesquisa.setText("");
        btnEliminar.setEnabled(false);
        btnSalvar.setText("Guardar");
        tabela.clearSelection();
        actualizar();
    }

    private void pesquisar() {
        String t = txtPesquisa.getText().trim();
        List<Produto> lista = t.isEmpty() ? service.listarProdutos() : service.buscarProdutosPorNome(t);
        popularTabela(lista);
    }

    private void popularTabela(List<Produto> lista) {
        tableModel.setRowCount(0);
        for (Produto p : lista) {
            String cat = p.getCategoria() != null ? p.getCategoria().getNome() : "—";
            String alerta = p.stockAbaixoMinimo() ? "⚠" : "";
            tableModel.addRow(new Object[]{
                p.getId(), p.getNome(),
                String.format("%.2f", p.getPreco()),
                p.getQuantidadeStock(),
                p.getUnidade(), cat, alerta
            });
        }
    }

    public void actualizar() {
        cboCategoria.removeAllItems();
        for (Categoria c : service.listarCategorias()) cboCategoria.addItem(c);
        popularTabela(service.listarProdutos());
    }
}
