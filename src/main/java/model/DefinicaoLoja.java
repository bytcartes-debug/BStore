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

    public DefinicaoLoja() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public DefinicaoLoja(Long usuarioId, Boolean controloCaixa, String nomeLoja) {
        this.usuarioId = usuarioId;
        this.controloCaixa = controloCaixa != null ? controloCaixa : false;
        this.nomeLoja = nomeLoja;
        this.ultimoNumeroVenda = 0L;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Boolean getControloCaixa() { return controloCaixa; }
    public void setControloCaixa(Boolean controloCaixa) { this.controloCaixa = controloCaixa; }

    public String getNomeLoja() { return nomeLoja; }
    public void setNomeLoja(String nomeLoja) { this.nomeLoja = nomeLoja; }

    public Long getUltimoNumeroVenda() { return ultimoNumeroVenda != null ? ultimoNumeroVenda : 0L; }
    public void setUltimoNumeroVenda(Long ultimoNumeroVenda) { this.ultimoNumeroVenda = ultimoNumeroVenda != null ? ultimoNumeroVenda : 0L; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
