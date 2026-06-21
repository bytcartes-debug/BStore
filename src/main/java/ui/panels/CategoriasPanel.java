package ui.panels;

import model.Categoria;
import service.BarracaService;
import ui.Cores;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class CategoriasPanel extends JPanel {

    private BarracaService service;
    private DefaultTableModel tableModel;
    private JTable tabela;
    private JTextField txtNome, txtDescricao, txtPesquisa;
    private JButton btnSalvar, btnLimpar, btnEliminar;
    private Long idEmEdicao = null;

    public CategoriasPanel(BarracaService service) {
        this.service = service;
        setLayout(new BorderLayout(15, 0));
        setBackground(Cores.CINZENTO_FUNDO);
        setBorder(new EmptyBorder(25, 25, 25, 25));
        construirUI();
    }

    private void construirUI() {
        JLabel titulo = new JLabel("Gestão de Categorias");
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
        form.setPreferredSize(new Dimension(280, 0));

        JLabel lblForm = new JLabel("Nova Categoria");
        lblForm.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblForm.setForeground(Cores.TEXTO_ESCURO);
        lblForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lblForm);
        form.add(Box.createVerticalStrut(20));

        form.add(criarLabel("Nome *"));
        txtNome = criarCampo();
        form.add(txtNome);
        form.add(Box.createVerticalStrut(12));

        form.add(criarLabel("Descrição"));
        txtDescricao = criarCampo();
        form.add(txtDescricao);
        form.add(Box.createVerticalStrut(24));

        btnSalvar = criarBotao("Guardar", Cores.VERDE_PRINCIPAL, Color.WHITE);
        btnSalvar.addActionListener(e -> salvar());
        form.add(btnSalvar);
        form.add(Box.createVerticalStrut(8));

        btnLimpar = criarBotao("Limpar", Cores.CINZENTO_FUNDO, Cores.TEXTO_ESCURO);
        btnLimpar.addActionListener(e -> limparFormulario());
        form.add(btnLimpar);
        form.add(Box.createVerticalStrut(8));

        btnEliminar = criarBotao("Eliminar", Cores.VERMELHO, Color.WHITE);
        btnEliminar.setEnabled(false);
        btnEliminar.addActionListener(e -> eliminar());
        form.add(btnEliminar);

        return form;
    }

    private JPanel criarPainelTabela() {
        JPanel painel = new JPanel(new BorderLayout(0, 10));
        painel.setOpaque(false);

        JPanel barraPesquisa = new JPanel(new BorderLayout(8, 0));
        barraPesquisa.setOpaque(false);
        txtPesquisa = new JTextField();
        txtPesquisa.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtPesquisa.putClientProperty("JTextField.placeholderText", "Pesquisar categoria...");
        txtPesquisa.addActionListener(e -> pesquisar());
        JButton btnPesquisar = criarBotao("Pesquisar", new Color(52, 152, 219), Color.WHITE);
        btnPesquisar.setPreferredSize(new Dimension(100, 35));
        btnPesquisar.addActionListener(e -> pesquisar());
        barraPesquisa.add(txtPesquisa, BorderLayout.CENTER);
        barraPesquisa.add(btnPesquisar, BorderLayout.EAST);
        painel.add(barraPesquisa, BorderLayout.NORTH);

        String[] colunas = {"ID", "Nome", "Descrição"};
        tableModel = new DefaultTableModel(colunas, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tabela = new JTable(tableModel);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.setRowHeight(32);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabela.getTableHeader().setBackground(Cores.VERDE_CLARO);
        tabela.setSelectionBackground(Cores.VERDE_CLARO);
        tabela.setGridColor(Cores.BORDA);
        tabela.getColumnModel().getColumn(0).setMaxWidth(60);
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selecionarLinha();
        });

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(new LineBorder(Cores.BORDA, 1, true));
        painel.add(scroll, BorderLayout.CENTER);

        return painel;
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(Cores.TEXTO_CINZENTO);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField criarCampo() {
        JTextField txt = new JTextField();
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        txt.setAlignmentX(Component.LEFT_ALIGNMENT);
        return txt;
    }

    private JButton criarBotao(String texto, Color fundo, Color letra) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(fundo);
        btn.setForeground(letra);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void salvar() {
        String nome = txtNome.getText().trim();
        String descricao = txtDescricao.getText().trim();

        try {
            if (idEmEdicao == null) {
                service.criarCategoria(nome, descricao);
                mostrarMensagem("Categoria criada com sucesso!", false);
            } else {
                Categoria c = service.buscarCategoria(idEmEdicao);
                c.setNome(nome);
                c.setDescricao(descricao);
                service.actualizarCategoria(c);
                mostrarMensagem("Categoria actualizada com sucesso!", false);
            }
            limparFormulario();
            actualizar();
        } catch (Exception ex) {
            mostrarMensagem("Erro: " + ex.getMessage(), true);
        }
    }

    private void eliminar() {
        if (idEmEdicao == null) return;
        int opcao = JOptionPane.showConfirmDialog(this,
            "Tem a certeza que deseja eliminar esta categoria?",
            "Confirmar eliminação", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcao == JOptionPane.YES_OPTION) {
            try {
                service.eliminarCategoria(idEmEdicao);
                mostrarMensagem("Categoria eliminada.", false);
                limparFormulario();
                actualizar();
            } catch (Exception ex) {
                mostrarMensagem("Erro: " + ex.getMessage(), true);
            }
        }
    }

    private void selecionarLinha() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) return;
        idEmEdicao = Long.parseLong(tableModel.getValueAt(linha, 0).toString());
        txtNome.setText(tableModel.getValueAt(linha, 1).toString());
        Object desc = tableModel.getValueAt(linha, 2);
        txtDescricao.setText(desc != null ? desc.toString() : "");
        btnEliminar.setEnabled(true);
        btnSalvar.setText("Actualizar");
    }

    private void limparFormulario() {
        idEmEdicao = null;
        txtNome.setText("");
        txtDescricao.setText("");
        txtPesquisa.setText("");
        btnEliminar.setEnabled(false);
        btnSalvar.setText("Guardar");
        tabela.clearSelection();
        actualizar();
    }

    private void pesquisar() {
        String termo = txtPesquisa.getText().trim();
        tableModel.setRowCount(0);
        List<Categoria> lista = termo.isEmpty() ? service.listarCategorias()
                                                : new dao.CategoriaDAO().buscarPorNome(termo);
        for (Categoria c : lista)
            tableModel.addRow(new Object[]{c.getId(), c.getNome(), c.getDescricao()});
    }

    private void mostrarMensagem(String msg, boolean erro) {
        if (erro) JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
        else JOptionPane.showMessageDialog(this, msg, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
    }

    public void actualizar() {
        tableModel.setRowCount(0);
        for (Categoria c : service.listarCategorias())
            tableModel.addRow(new Object[]{c.getId(), c.getNome(), c.getDescricao()});
    }
}
