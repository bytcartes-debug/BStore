package model;

import javax.persistence.*;
import java.math.BigDecimal;
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

    @Column(name = "preco", nullable = false, precision = 19, scale = 2)
    private BigDecimal preco;

    @Column(name = "custo", nullable = false, precision = 19, scale = 2)
    private BigDecimal custo = BigDecimal.ZERO;

    @Column(name = "quantidade_stock", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantidadeStock;

    @Column(name = "stock_minimo", precision = 19, scale = 3)
    private BigDecimal stockMinimo = new BigDecimal("5.000");

    @Column(name = "stock_maximo", precision = 19, scale = 3)
    private BigDecimal stockMaximo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /** Unidade de medida: "un", "kg", "L", "g", "ml", etc. */
    @Column(name = "unidade", length = 50)
    private String unidade = "un";

    @Column(name = "codigo_barras", length = 100)
    private String codigoBarras;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @OneToMany(mappedBy = "produto", fetch = FetchType.LAZY)
    private List<ItemVenda> itensVenda = new ArrayList<>();

    public Produto() {}

    public Produto(String nome, BigDecimal preco, BigDecimal quantidadeStock, String unidade, Categoria categoria) {
        this.nome            = nome;
        this.preco           = preco;
        this.quantidadeStock = quantidadeStock;
        this.unidade         = (unidade != null && !unidade.isBlank()) ? unidade : "un";
        this.categoria       = categoria;
    }

    public boolean stockAbaixoMinimo() {
        return quantidadeStock != null && stockMinimo != null && quantidadeStock.compareTo(stockMinimo) <= 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public BigDecimal getCusto() { return custo != null ? custo : BigDecimal.ZERO; }
    public void setCusto(BigDecimal custo) { this.custo = (custo != null) ? custo : BigDecimal.ZERO; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public BigDecimal getQuantidadeStock() { return quantidadeStock; }
    public void setQuantidadeStock(BigDecimal quantidadeStock) { this.quantidadeStock = quantidadeStock; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getStockMaximo() { return stockMaximo; }
    public void setStockMaximo(BigDecimal stockMaximo) { this.stockMaximo = stockMaximo; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String u) { this.unidade = (u != null && !u.isBlank()) ? u : "un"; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String c) { this.codigoBarras = (c != null && c.isBlank()) ? null : c; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long u) { this.usuarioId = u; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria c) { this.categoria = c; }

    public List<ItemVenda> getItensVenda() { return itensVenda; }

    @Override public String toString() { return nome; }
}
