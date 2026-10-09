package model;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pagamentos_venda")
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Column(name = "metodo", nullable = false, length = 15)
    private String metodo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "metodo_id")
    private MetodoPagamento metodoPagamento;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "troco", nullable = false, precision = 19, scale = 2)
    private BigDecimal troco = BigDecimal.ZERO;

    public PagamentoVenda() {}

    public PagamentoVenda(Long usuarioId, Venda venda, String metodo, BigDecimal valor, BigDecimal troco) {
        this(usuarioId, venda, metodo, null, null, valor, troco);
    }

    public PagamentoVenda(Long usuarioId, Venda venda, String metodo, MetodoPagamento metodoPagamento, String referencia, BigDecimal valor, BigDecimal troco) {
        this.usuarioId = usuarioId;
        this.venda = venda;
        this.metodo = metodo;
        this.metodoPagamento = metodoPagamento;
        this.referencia = referencia;
        this.valor = valor;
        this.troco = troco != null ? troco : BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public MetodoPagamento getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(MetodoPagamento metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public BigDecimal getTroco() { return troco; }
    public void setTroco(BigDecimal troco) { this.troco = troco; }
}
