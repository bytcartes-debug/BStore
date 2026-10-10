package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "despesas_caixa")
public class DespesaCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_id")
    private SessaoCaixa sessaoCaixa;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria = "OUTRO";

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "criado_por")
    private Long criadoPor;

    public DespesaCaixa() {
        this.criadaEm = LocalDateTime.now();
    }

    public DespesaCaixa(Long usuarioId, SessaoCaixa sessaoCaixa, BigDecimal valor, String categoria, String descricao, Long criadoPor) {
        this.usuarioId = usuarioId;
        this.sessaoCaixa = sessaoCaixa;
        this.valor = valor != null ? valor : BigDecimal.ZERO;
        this.categoria = categoria != null && !categoria.trim().isEmpty() ? categoria.trim().toUpperCase() : "OUTRO";
        this.descricao = descricao != null ? descricao.trim() : "";
        this.criadoPor = criadoPor;
        this.criadaEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public SessaoCaixa getSessaoCaixa() { return sessaoCaixa; }
    public void setSessaoCaixa(SessaoCaixa sessaoCaixa) { this.sessaoCaixa = sessaoCaixa; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }

    public Long getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Long criadoPor) { this.criadoPor = criadoPor; }
}
