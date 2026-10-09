package model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "metodos_pagamento")
public class MetodoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "nome", nullable = false, length = 50)
    private String nome;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo; // DINHEIRO, DIGITAL, FIADO

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public MetodoPagamento() {
        this.criadoEm = LocalDateTime.now();
    }

    public MetodoPagamento(Long usuarioId, String nome, String tipo, Boolean ativo, Integer ordem) {
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.tipo = tipo;
        this.ativo = ativo != null ? ativo : true;
        this.ordem = ordem != null ? ordem : 0;
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
