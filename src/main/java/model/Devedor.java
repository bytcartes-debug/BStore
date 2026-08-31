package model;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "devedores")
public class Devedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private Double divida;

    private String descricao;

    private LocalDate data = LocalDate.now();

    public Devedor() {}

    public Long getId()          { return id; }
    public String getNome()      { return nome; }
    public Double getDivida()    { return divida; }
    public String getDescricao() { return descricao; }
    public LocalDate getData()   { return data; }

    public void setId(Long id)             { this.id = id; }
    public void setNome(String nome)       { this.nome = nome; }
    public void setDivida(Double divida)   { this.divida = divida; }
    public void setDescricao(String d)     { this.descricao = d; }
    public void setData(LocalDate data)    { this.data = data; }
}
