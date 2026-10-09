package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendas")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "numero", nullable = false)
    private Long numero;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "total_custo", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCusto = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 15)
    private String estado = "CONCLUIDA";

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "observacao", length = 255)
    private String observacao;

    @Column(name = "anulada_em")
    private LocalDateTime anuladaEm;

    @Column(name = "anulada_por")
    private Long anuladaPor;

    @Column(name = "motivo_anulacao", length = 255)
    private String motivoAnulacao;

    @Column(name = "criado_por")
    private Long criadoPor;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

    public Venda() {
        this.criadaEm = LocalDateTime.now();
    }

    public Venda(Long usuarioId, Long numero, LocalDateTime criadaEm, BigDecimal total, BigDecimal totalCusto, String estado, Long clienteId, String observacao, Long criadoPor) {
        this.usuarioId = usuarioId;
        this.numero = numero;
        this.criadaEm = criadaEm != null ? criadaEm : LocalDateTime.now();
        this.total = total;
        this.totalCusto = totalCusto != null ? totalCusto : BigDecimal.ZERO;
        this.estado = estado != null ? estado : "CONCLUIDA";
        this.clienteId = clienteId;
        this.observacao = observacao;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getNumero() { return numero; }
    public void setNumero(Long numero) { this.numero = numero; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }

    public LocalDate getDataVenda() { return criadaEm != null ? criadaEm.toLocalDate() : LocalDate.now(); }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getTotalCusto() { return totalCusto; }
    public void setTotalCusto(BigDecimal totalCusto) { this.totalCusto = totalCusto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public LocalDateTime getAnuladaEm() { return anuladaEm; }
    public void setAnuladaEm(LocalDateTime anuladaEm) { this.anuladaEm = anuladaEm; }

    public Long getAnuladaPor() { return anuladaPor; }
    public void setAnuladaPor(Long anuladaPor) { this.anuladaPor = anuladaPor; }

    public String getMotivoAnulacao() { return motivoAnulacao; }
    public void setMotivoAnulacao(String motivoAnulacao) { this.motivoAnulacao = motivoAnulacao; }

    public Long getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Long criadoPor) { this.criadoPor = criadoPor; }

    public List<ItemVenda> getItens() { return itens; }
    public void setItens(List<ItemVenda> itens) {
        if (this.itens == null) {
            this.itens = new ArrayList<>();
        } else {
            this.itens.clear();
        }
        if (itens != null) {
            this.itens.addAll(itens);
        }
    }

    public List<PagamentoVenda> getPagamentos() { return pagamentos; }
    public void setPagamentos(List<PagamentoVenda> pagamentos) {
        if (this.pagamentos == null) {
            this.pagamentos = new ArrayList<>();
        } else {
            this.pagamentos.clear();
        }
        if (pagamentos != null) {
            this.pagamentos.addAll(pagamentos);
        }
    }

    public void addItem(ItemVenda item) {
        itens.add(item);
        item.setVenda(this);
    }

    public void addPagamento(PagamentoVenda pagamento) {
        pagamentos.add(pagamento);
        pagamento.setVenda(this);
    }
}
