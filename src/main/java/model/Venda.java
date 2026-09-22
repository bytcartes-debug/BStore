package model;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "vendas")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDate dataVenda;

    @Column(name = "quantidade", nullable = false)
    private Double quantidade;

    @Column(name = "preco_unitario", nullable = false)
    private Double precoUnitario;

    @Column(name = "total", nullable = false)
    private Double total;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "usuario_id")
    private Long usuarioId;

    public Venda() {}

    public Venda(LocalDate dataVenda, Double quantidade, Produto produto) {
        this.dataVenda     = dataVenda;
        this.quantidade    = quantidade;
        this.produto       = produto;
        this.precoUnitario = produto.getPreco();
        this.total         = quantidade * produto.getPreco();
    }

    public Venda(LocalDate dataVenda, Double quantidade, Produto produto, String observacao) {
        this(dataVenda, quantidade, produto);
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate d) { this.dataVenda = d; }

    public Double getQuantidade() { return quantidade; }
    public void setQuantidade(Double q) { this.quantidade = q; }

    public Double getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(Double p) { this.precoUnitario = p; }

    public Double getTotal() { return total; }
    public void setTotal(Double t) { this.total = t; }

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
