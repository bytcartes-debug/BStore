package model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos_venda")
@IdClass(PedidoVendaId.class)
public class PedidoVenda {
    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Id
    @Column(nullable = false, length = 80)
    private String chave;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resposta;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public PedidoVenda() {}
    public PedidoVenda(Long usuarioId, String chave, String fingerprint) {
        this.usuarioId = usuarioId;
        this.chave = chave;
        this.fingerprint = fingerprint;
        this.resposta = "";
        this.criadoEm = LocalDateTime.now();
    }

    public String getFingerprint() { return fingerprint; }
    public String getResposta() { return resposta; }
    public void setResposta(String resposta) { this.resposta = resposta; }
}
