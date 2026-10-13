package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ia_uso")
public class IaUso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "operacao", length = 64, nullable = false)
    private String operacao;

    @Column(name = "tokens_entrada", nullable = false)
    private Integer tokensEntrada = 0;

    @Column(name = "tokens_saida", nullable = false)
    private Integer tokensSaida = 0;

    @Column(name = "custo_estimado", precision = 10, scale = 4, nullable = false)
    private BigDecimal custoEstimado = BigDecimal.ZERO;

    @Column(name = "sucesso", nullable = false)
    private Boolean sucesso = true;

    @Column(name = "mensagem_erro", columnDefinition = "TEXT")
    private String mensagemErro;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = util.Fuso.agora();
        }
        if (this.custoEstimado == null) {
            this.custoEstimado = BigDecimal.ZERO;
        }
    }

    public IaUso() {
        this.criadoEm = util.Fuso.agora();
    }

    public IaUso(Long usuarioId, String operacao, Integer tokensEntrada, Integer tokensSaida,
                 BigDecimal custoEstimado, Boolean sucesso, String mensagemErro) {
        this.usuarioId = usuarioId;
        this.operacao = operacao;
        this.tokensEntrada = tokensEntrada != null ? tokensEntrada : 0;
        this.tokensSaida = tokensSaida != null ? tokensSaida : 0;
        this.custoEstimado = custoEstimado != null ? custoEstimado : BigDecimal.ZERO;
        this.sucesso = sucesso != null ? sucesso : true;
        this.mensagemErro = mensagemErro;
        this.criadoEm = util.Fuso.agora();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getOperacao() { return operacao; }
    public void setOperacao(String operacao) { this.operacao = operacao; }

    public Integer getTokensEntrada() { return tokensEntrada; }
    public void setTokensEntrada(Integer tokensEntrada) { this.tokensEntrada = tokensEntrada; }

    public Integer getTokensSaida() { return tokensSaida; }
    public void setTokensSaida(Integer tokensSaida) { this.tokensSaida = tokensSaida; }

    public BigDecimal getCustoEstimado() { return custoEstimado; }
    public void setCustoEstimado(BigDecimal custoEstimado) { this.custoEstimado = custoEstimado; }

    public Boolean getSucesso() { return sucesso; }
    public void setSucesso(Boolean sucesso) { this.sucesso = sucesso; }

    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
