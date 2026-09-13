package model;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 64)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role; // "superuser" ou "operator"

    /** Null = acesso permanente. Preenchido = expira nessa data. */
    @Column(name = "data_expiracao")
    private LocalDate dataExpiracao;

    /** 14, 30 ou 0 (permanente) — para referência ao renovar. */
    @Column(name = "dias_acesso")
    private Integer diasAcesso;

    public Usuario() {}

    public Usuario(String nome, String email, String senhaPlana, String role) {
        this.nome         = nome;
        this.email        = email.toLowerCase().trim();
        this.passwordHash = hashSenha(senhaPlana);
        this.role         = role;
    }

    public boolean verificarSenha(String senhaPlana) {
        return hashSenha(senhaPlana).equals(this.passwordHash);
    }

    /** Retorna true se a conta está expirada. Superuser e permanente nunca expiram. */
    public boolean isExpirado() {
        if ("superuser".equals(role)) return false;
        if (dataExpiracao == null) return false;
        return LocalDate.now().isAfter(dataExpiracao);
    }

    /** Dias restantes. -1 = permanente/superuser. >= 0 = dias até expirar. */
    public long diasRestantes() {
        if ("superuser".equals(role) || dataExpiracao == null) return -1L;
        long d = ChronoUnit.DAYS.between(LocalDate.now(), dataExpiracao);
        return Math.max(d, 0);
    }

    /** Define expiração a partir de hoje + dias. 0 = permanente. */
    public void aplicarDiasAcesso(int dias) {
        this.diasAcesso    = dias;
        this.dataExpiracao = (dias > 0) ? LocalDate.now().plusDays(dias) : null;
    }

    public static String hashSenha(String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(senha.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return senha; }
    }

    // ── Getters / Setters ──────────────────────────────────────────────
    public Long   getId()        { return id; }
    public void   setId(Long id) { this.id = id; }

    public String getNome()            { return nome; }
    public void   setNome(String n)    { this.nome = n; }

    public String getEmail()             { return email; }
    public void   setEmail(String e)     { this.email = e.toLowerCase().trim(); }

    public String getPasswordHash()      { return passwordHash; }
    public void   setPasswordHash(String h) { this.passwordHash = h; }
    public void   setSenha(String s)     { this.passwordHash = hashSenha(s); }

    public String getRole()            { return role; }
    public void   setRole(String r)    { this.role = r; }

    public LocalDate getDataExpiracao()      { return dataExpiracao; }
    public void      setDataExpiracao(LocalDate d) { this.dataExpiracao = d; }

    public Integer getDiasAcesso()       { return diasAcesso; }
    public void    setDiasAcesso(Integer d) { this.diasAcesso = d; }
}
