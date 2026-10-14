package model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "definicoes_loja")
public class DefinicaoLoja {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "controlo_caixa", nullable = false)
    private Boolean controloCaixa = false;

    @Column(name = "nome_loja", length = 100)
    private String nomeLoja;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "ultimo_numero_venda", nullable = false)
    private Long ultimoNumeroVenda = 0L;

    @Column(name = "ia_activa", nullable = false)
    private Boolean iaActiva = true;

    @Column(name = "ia_provedor", length = 32, nullable = false)
    private String iaProvedor = "gemini";

    @Column(name = "ia_api_key", length = 255)
    private String iaApiKey;

    @Column(name = "ia_limite_diario", nullable = false)
    private Integer iaLimiteDiario = 50;

    @Column(name = "ia_limite_mensal", nullable = false)
    private Integer iaLimiteMensal = 500;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        this.atualizadoEm = util.Fuso.agora();
    }

    public DefinicaoLoja() {
        this.atualizadoEm = util.Fuso.agora();
    }

    public DefinicaoLoja(Long usuarioId, Boolean controloCaixa, String nomeLoja) {
        this.usuarioId = usuarioId;
        this.controloCaixa = controloCaixa != null ? controloCaixa : false;
        this.nomeLoja = nomeLoja;
        this.ultimoNumeroVenda = 0L;
        this.iaActiva = true;
        this.iaProvedor = "gemini";
        this.iaLimiteDiario = 50;
        this.iaLimiteMensal = 500;
        this.atualizadoEm = util.Fuso.agora();
    }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Boolean getControloCaixa() { return controloCaixa; }
    public void setControloCaixa(Boolean controloCaixa) { this.controloCaixa = controloCaixa; }

    public String getNomeLoja() { return nomeLoja; }
    public void setNomeLoja(String nomeLoja) { this.nomeLoja = nomeLoja; }

    public Long getUltimoNumeroVenda() { return ultimoNumeroVenda != null ? ultimoNumeroVenda : 0L; }
    public void setUltimoNumeroVenda(Long ultimoNumeroVenda) { this.ultimoNumeroVenda = ultimoNumeroVenda != null ? ultimoNumeroVenda : 0L; }

    public Boolean getIaActiva() { return iaActiva != null ? iaActiva : true; }
    public void setIaActiva(Boolean iaActiva) { this.iaActiva = iaActiva != null ? iaActiva : true; }

    public String getIaProvedor() { return iaProvedor != null ? iaProvedor : "gemini"; }
    public void setIaProvedor(String iaProvedor) { this.iaProvedor = iaProvedor != null ? iaProvedor : "gemini"; }

    public String getIaApiKey() { return iaApiKey; }
    public void setIaApiKey(String iaApiKey) { this.iaApiKey = iaApiKey; }

    public Integer getIaLimiteDiario() { return iaLimiteDiario != null ? iaLimiteDiario : 50; }
    public void setIaLimiteDiario(Integer iaLimiteDiario) { this.iaLimiteDiario = iaLimiteDiario != null ? iaLimiteDiario : 50; }

    public Integer getIaLimiteMensal() { return iaLimiteMensal != null ? iaLimiteMensal : 500; }
    public void setIaLimiteMensal(Integer iaLimiteMensal) { this.iaLimiteMensal = iaLimiteMensal != null ? iaLimiteMensal : 500; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
