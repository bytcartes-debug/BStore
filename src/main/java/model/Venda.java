package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "vendas")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    @Column(name = "quantidade", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "usuario_id")
    private Long usuarioId;

    public Venda() {}

    public Venda(LocalDate dataVenda, BigDecimal quantidade, Produto produto) {
        this.dataVenda = dataVenda;
        this.quantidade = quantidade;
        this.produto = produto;
        this.precoUnitario = produto.getPreco();
        this.total = quantidade.multiply(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
    }

    public Venda(LocalDate dataVenda, BigDecimal quantidade, Produto produto, String observacao) {
        this(dataVenda, quantidade, produto);
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate d) { this.dataVenda = d; }

    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String o) { this.observacao = o; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto p) { this.produto = p; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long u) { this.usuarioId = u; }

    @Override
    public String toString() {
        return "Venda #" + id + " - " + produto.getNome() + " x" + quantidade;
    }
}
