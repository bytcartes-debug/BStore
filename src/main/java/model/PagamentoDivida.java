package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos_divida")
public class PagamentoDivida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devedor_id", nullable = false)
    private Devedor devedor;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "metodo", nullable = false, length = 15)
    private String metodo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "observacao", length = 255)
    private String observacao;

    public PagamentoDivida() {}

    public PagamentoDivida(Long usuarioId, Devedor devedor, BigDecimal valor, String metodo, LocalDateTime criadoEm, String observacao) {
        this.usuarioId = usuarioId;
        this.devedor = devedor;
        this.valor = valor;
        this.metodo = metodo;
        this.criadoEm = criadoEm != null ? criadoEm : LocalDateTime.now();
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Devedor getDevedor() { return devedor; }
    public void setDevedor(Devedor devedor) { this.devedor = devedor; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
