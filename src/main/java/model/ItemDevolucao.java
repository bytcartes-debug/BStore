package model;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "devolucao_itens")
public class ItemDevolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucao_id", nullable = false)
    private Devolucao devolucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_venda_id", nullable = false)
    private ItemVenda itemVenda;

    @Column(name = "quantidade", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    public ItemDevolucao() {}

    public ItemDevolucao(Devolucao devolucao, ItemVenda itemVenda, BigDecimal quantidade, BigDecimal valor) {
        this.devolucao = devolucao;
        this.itemVenda = itemVenda;
        this.quantidade = quantidade;
        this.valor = valor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Devolucao getDevolucao() { return devolucao; }
    public void setDevolucao(Devolucao devolucao) { this.devolucao = devolucao; }

    public ItemVenda getItemVenda() { return itemVenda; }
    public void setItemVenda(ItemVenda itemVenda) { this.itemVenda = itemVenda; }

    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
