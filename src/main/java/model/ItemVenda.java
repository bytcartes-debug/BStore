package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "venda_itens")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "desconto_percentual", nullable = false, precision = 19, scale = 2)
    private BigDecimal descontoPercentual = BigDecimal.ZERO;

    @Column(name = "desconto_valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(name = "preco_final", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoFinal;

    @Column(name = "custo_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal custoUnitario = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @Column(name = "nota", length = 255)
    private String nota;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    public ItemVenda() {}

    public ItemVenda(Venda venda, Produto produto, BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal custoUnitario, String observacao, Long usuarioId) {
        this(venda, produto, quantidade, precoUnitario, BigDecimal.ZERO, BigDecimal.ZERO, precoUnitario, custoUnitario, observacao, null, usuarioId);
    }

    public ItemVenda(Venda venda, Produto produto, BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal descontoPercentual, BigDecimal descontoValor, BigDecimal precoFinal, BigDecimal custoUnitario, String observacao, String nota, Long usuarioId) {
        this.venda = venda;
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.descontoPercentual = descontoPercentual != null ? descontoPercentual : BigDecimal.ZERO;
        this.descontoValor = descontoValor != null ? descontoValor : BigDecimal.ZERO;
        this.precoFinal = precoFinal != null ? precoFinal : precoUnitario;
        this.custoUnitario = custoUnitario != null ? custoUnitario : BigDecimal.ZERO;
        this.total = quantidade.multiply(this.precoFinal).setScale(2, RoundingMode.HALF_UP);
        this.dataVenda = venda != null && venda.getCriadaEm() != null ? venda.getCriadaEm().toLocalDate() : LocalDate.now();
        this.observacao = observacao;
        this.nota = nota;
        this.usuarioId = usuarioId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getDescontoValor() { return descontoValor; }
    public void setDescontoValor(BigDecimal descontoValor) { this.descontoValor = descontoValor; }

    public BigDecimal getPrecoFinal() { return precoFinal; }
    public void setPrecoFinal(BigDecimal precoFinal) { this.precoFinal = precoFinal; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public void setCustoUnitario(BigDecimal custoUnitario) { this.custoUnitario = custoUnitario; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate dataVenda) { this.dataVenda = dataVenda; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
}
