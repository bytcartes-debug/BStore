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
    private Integer quantidade;

    @Column(name = "preco_unitario", nullable = false)
    private Double precoUnitario;

    @Column(name = "total", nullable = false)
    private Double total;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    public Venda() {}

    public Venda(LocalDate dataVenda, Integer quantidade, Produto produto) {
        this.dataVenda = dataVenda;
        this.quantidade = quantidade;
        this.produto = produto;
        this.precoUnitario = produto.getPreco();
        this.total = quantidade * produto.getPreco();
    }

    public Venda(LocalDate dataVenda, Integer quantidade, Produto produto, String observacao) {
        this(dataVenda, quantidade, produto);
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDate dataVenda) { this.dataVenda = dataVenda; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Double getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(Double precoUnitario) { this.precoUnitario = precoUnitario; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    @Override
    public String toString() {
        return "Venda #" + id + " - " + produto.getNome() + " x" + quantidade;
    }
}
