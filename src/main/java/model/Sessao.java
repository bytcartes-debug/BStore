package model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sessoes")
public class Sessao {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    public Sessao() {
    }

    public Sessao(String id, Usuario usuario, Instant criadaEm, Instant expiraEm) {
        this.id = id;
        this.usuario = usuario;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
    }

    public boolean estaActiva(Instant agora) {
        return revogadaEm == null && expiraEm.isAfter(agora);
    }

    public String getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getRevogadaEm() { return revogadaEm; }
    public void revogar(Instant data) { this.revogadaEm = data; }
}
