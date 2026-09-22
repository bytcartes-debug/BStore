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
    private Double quantidadeStock;

    @Column(name = "stock_minimo")
    private Double stockMinimo = 5.0;

    /** Unidade de medida: "un", "kg", "L", "g", "ml", etc. */
    @Column(name = "unidade", length = 50)
    private String unidade = "un";

    @Column(name = "usuario_id")
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Venda> vendas = new ArrayList<>();

    public Produto() {}

    public Produto(String nome, Double preco, Double quantidadeStock, String unidade, Categoria categoria) {
        this.nome            = nome;
        this.preco           = preco;
        this.quantidadeStock = quantidadeStock;
        this.unidade         = (unidade != null && !unidade.isBlank()) ? unidade : "un";
        this.categoria       = categoria;
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

    public Double getQuantidadeStock() { return quantidadeStock; }
    public void setQuantidadeStock(Double q) { this.quantidadeStock = q; }

    public Double getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Double s) { this.stockMinimo = s; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String u) { this.unidade = (u != null && !u.isBlank()) ? u : "un"; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long u) { this.usuarioId = u; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria c) { this.categoria = c; }

    public List<Venda> getVendas() { return vendas; }

    @Override public String toString() { return nome; }
}
