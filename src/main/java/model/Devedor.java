package model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "devedores")
public class Devedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal divida;

    private String descricao;

    private LocalDate data = LocalDate.now();

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "venda_id")
    private Long vendaId;

    public Devedor() {}

    public Long getId()          { return id; }
    public String getNome()      { return nome; }
    public BigDecimal getDivida() { return divida; }
    public String getDescricao() { return descricao; }
    public LocalDate getData()   { return data; }
    public Long getVendaId()     { return vendaId; }

    public void setId(Long id)             { this.id = id; }
    public void setNome(String nome)       { this.nome = nome; }
    public void setDivida(BigDecimal divida) { this.divida = divida; }
    public void setDescricao(String d)     { this.descricao = d; }
    public void setData(LocalDate data)    { this.data = data; }
    public void setVendaId(Long vendaId)   { this.vendaId = vendaId; }

    public Long getUsuarioId()             { return usuarioId; }
    public void setUsuarioId(Long uid)     { this.usuarioId = uid; }
}
