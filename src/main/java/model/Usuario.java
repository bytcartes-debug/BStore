package model;

import org.mindrot.jbcrypt.BCrypt;

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

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "data_expiracao")
    private LocalDate dataExpiracao;

    @Column(name = "dias_acesso")
    private Integer diasAcesso;

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaPlana, String role) {
        this.nome = nome;
        this.email = email.toLowerCase().trim();
        this.role = role;
        setSenha(senhaPlana);
    }

    public boolean verificarSenha(String senhaPlana) {
        if (senhaPlana == null || passwordHash == null) {
            return false;
        }
        if (passwordHash.startsWith("$2a$") || passwordHash.startsWith("$2b$") || passwordHash.startsWith("$2y$")) {
            return BCrypt.checkpw(senhaPlana, passwordHash);
        }
        return MessageDigest.isEqual(hashLegado(senhaPlana).getBytes(StandardCharsets.US_ASCII), passwordHash.getBytes(StandardCharsets.US_ASCII));
    }

    public boolean usaHashLegado() {
        return passwordHash != null && !passwordHash.startsWith("$2");
    }

    public boolean isExpirado() {
        if ("superuser".equals(role) || dataExpiracao == null) {
            return false;
        }
        return LocalDate.now().isAfter(dataExpiracao);
    }

    public long diasRestantes() {
        if ("superuser".equals(role) || dataExpiracao == null) {
            return -1L;
        }
        return Math.max(ChronoUnit.DAYS.between(LocalDate.now(), dataExpiracao), 0);
    }

    public void aplicarDiasAcesso(int dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Os dias de acesso não podem ser negativos.");
        }
        this.diasAcesso = dias;
        this.dataExpiracao = dias > 0 ? LocalDate.now().plusDays(dias) : null;
    }

    private static String hashLegado(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(senha.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte b : hash) {
                value.append(String.format("%02x", b));
            }
            return value.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível verificar a palavra-passe.", e);
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("A palavra-passe deve ter pelo menos 8 caracteres.");
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email.toLowerCase().trim(); }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setSenha(String senha) {
        validarSenha(senha);
        this.passwordHash = BCrypt.hashpw(senha, BCrypt.gensalt(12));
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDate getDataExpiracao() { return dataExpiracao; }
    public void setDataExpiracao(LocalDate dataExpiracao) { this.dataExpiracao = dataExpiracao; }

    public Integer getDiasAcesso() { return diasAcesso; }
    public void setDiasAcesso(Integer diasAcesso) { this.diasAcesso = diasAcesso; }
}
