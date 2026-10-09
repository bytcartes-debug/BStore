package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devolucoes")
public class Devolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Column(name = "uuid_cliente", length = 100)
    private String uuidCliente;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @Column(name = "criado_por")
    private Long criadoPor;

    @OneToMany(mappedBy = "devolucao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemDevolucao> itens = new ArrayList<>();

    public Devolucao() {
        this.criadaEm = LocalDateTime.now();
    }

    public Devolucao(Long usuarioId, Venda venda, BigDecimal total, String motivo, Long criadoPor) {
        this(usuarioId, venda, total, motivo, criadoPor, null);
    }

    public Devolucao(Long usuarioId, Venda venda, BigDecimal total, String motivo, Long criadoPor, String uuidCliente) {
        this.usuarioId = usuarioId;
        this.venda = venda;
        this.total = total;
        this.motivo = motivo;
        this.criadoPor = criadoPor;
        this.uuidCliente = uuidCliente;
        this.criadaEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public Long getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Long criadoPor) { this.criadoPor = criadoPor; }

    public List<ItemDevolucao> getItens() { return itens; }
    public void setItens(List<ItemDevolucao> itens) {
        if (this.itens == null) {
            this.itens = new ArrayList<>();
        } else {
            this.itens.clear();
        }
        if (itens != null) {
            this.itens.addAll(itens);
        }
    }

    public void addItem(ItemDevolucao item) {
        this.itens.add(item);
        item.setDevolucao(this);
    }

    public String getUuidCliente() { return uuidCliente; }
    public void setUuidCliente(String uuidCliente) { this.uuidCliente = uuidCliente; }
}
