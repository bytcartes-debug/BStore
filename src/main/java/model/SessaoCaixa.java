package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessoes_caixa")
public class SessaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm;

    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    @Column(name = "valor_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorInicial = BigDecimal.ZERO;

    @Column(name = "valor_contado", precision = 19, scale = 2)
    private BigDecimal valorContado;

    @Column(name = "valor_esperado", precision = 19, scale = 2)
    private BigDecimal valorEsperado;

    @Column(name = "diferenca", precision = 19, scale = 2)
    private BigDecimal diferenca;

    @Column(name = "nota_abertura", length = 255)
    private String notaAbertura;

    @Column(name = "nota_fecho", length = 255)
    private String notaFecho;

    @Column(name = "aberta_por")
    private Long abertaPor;

    @Column(name = "fechada_por")
    private Long fechadaPor;

    @Column(name = "estado", nullable = false, length = 15)
    private String estado = "ABERTA"; // ABERTA, FECHADA

    public SessaoCaixa() {
        this.abertaEm = LocalDateTime.now();
    }

    public SessaoCaixa(Long usuarioId, BigDecimal valorInicial, String notaAbertura, Long abertaPor) {
        this.usuarioId = usuarioId;
        this.valorInicial = valorInicial != null ? valorInicial : BigDecimal.ZERO;
        this.notaAbertura = notaAbertura;
        this.abertaPor = abertaPor;
        this.abertaEm = LocalDateTime.now();
        this.estado = "ABERTA";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public LocalDateTime getAbertaEm() { return abertaEm; }
    public void setAbertaEm(LocalDateTime abertaEm) { this.abertaEm = abertaEm; }

    public LocalDateTime getFechadaEm() { return fechadaEm; }
    public void setFechadaEm(LocalDateTime fechadaEm) { this.fechadaEm = fechadaEm; }

    public BigDecimal getValorInicial() { return valorInicial; }
    public void setValorInicial(BigDecimal valorInicial) { this.valorInicial = valorInicial; }

    public BigDecimal getValorContado() { return valorContado; }
    public void setValorContado(BigDecimal valorContado) { this.valorContado = valorContado; }

    public BigDecimal getValorEsperado() { return valorEsperado; }
    public void setValorEsperado(BigDecimal valorEsperado) { this.valorEsperado = valorEsperado; }

    public BigDecimal getDiferenca() { return diferenca; }
    public void setDiferenca(BigDecimal diferenca) { this.diferenca = diferenca; }

    public String getNotaAbertura() { return notaAbertura; }
    public void setNotaAbertura(String notaAbertura) { this.notaAbertura = notaAbertura; }

    public String getNotaFecho() { return notaFecho; }
    public void setNotaFecho(String notaFecho) { this.notaFecho = notaFecho; }

    public Long getAbertaPor() { return abertaPor; }
    public void setAbertaPor(Long abertaPor) { this.abertaPor = abertaPor; }

    public Long getFechadaPor() { return fechadaPor; }
    public void setFechadaPor(Long fechadaPor) { this.fechadaPor = fechadaPor; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
