package model;

import util.Fuso;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_nome", length = 100)
    private String usuarioNome;

    @Column(name = "tipo_entidade", nullable = false, length = 50)
    private String tipoEntidade;

    @Column(name = "acao", nullable = false, length = 50)
    private String acao;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(name = "detalhes", columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Fuso.agora();
        }
    }

    public RegistroAuditoria() {
        this.criadoEm = Fuso.agora();
    }

    public RegistroAuditoria(Long usuarioId, String usuarioNome, String tipoEntidade, String acao, Long entidadeId, String detalhes, String ipOrigem) {
        this.usuarioId = usuarioId;
        this.usuarioNome = usuarioNome != null ? usuarioNome.trim() : null;
        this.tipoEntidade = tipoEntidade != null ? tipoEntidade.trim().toUpperCase() : "SISTEMA";
        this.acao = acao != null ? acao.trim().toUpperCase() : "ACAO";
        this.entidadeId = entidadeId;
        this.detalhes = detalhes != null ? detalhes.trim() : "";
        this.ipOrigem = ipOrigem != null ? ipOrigem.trim() : null;
        this.criadoEm = Fuso.agora();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }

    public String getTipoEntidade() { return tipoEntidade; }
    public void setTipoEntidade(String tipoEntidade) { this.tipoEntidade = tipoEntidade; }

    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }

    public Long getEntidadeId() { return entidadeId; }
    public void setEntidadeId(Long entidadeId) { this.entidadeId = entidadeId; }

    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String detalhes) { this.detalhes = detalhes; }

    public String getIpOrigem() { return ipOrigem; }
    public void setIpOrigem(String ipOrigem) { this.ipOrigem = ipOrigem; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
