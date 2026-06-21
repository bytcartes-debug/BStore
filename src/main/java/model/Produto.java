package model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "preco", nullable = false)
    private Double preco;

    @Column(name = "quantidade_stock", nullable = false)
    private Integer quantidadeStock;

    @Column(name = "stock_minimo")
    private Integer stockMinimo = 5;

    @Column(name = "unidade", length = 50)
    private String unidade;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Venda> vendas = new ArrayList<>();

    public Produto() {}

    public Produto(String nome, Double preco, Integer quantidadeStock, String unidade, Categoria categoria) {
        this.nome = nome;
        this.preco = preco;
        this.quantidadeStock = quantidadeStock;
        this.unidade = unidade;
        this.categoria = categoria;
    }

    public boolean stockAbaixoMinimo() {
        return quantidadeStock != null && stockMinimo != null && quantidadeStock <= stockMinimo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Double getPreco() { return preco; }
    public void setPreco(Double preco) { this.preco = preco; }

    public Integer getQuantidadeStock() { return quantidadeStock; }
    public void setQuantidadeStock(Integer quantidadeStock) { this.quantidadeStock = quantidadeStock; }

    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public List<Venda> getVendas() { return vendas; }

    @Override
    public String toString() {
        return nome;
    }
}
