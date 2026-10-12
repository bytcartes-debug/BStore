import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.RateLimiterLogin;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterLoginTest {

    private RateLimiterLogin limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiterLogin(5, 600_000L); // 5 tentativas, 10 minutos (600.000 ms)
    }

    @Test
    void bloqueioPorEmailPersisteMesmoComXffOuIpAlternado() {
        String emailKey = "email:alvo@exemplo.com";
        long agora = System.currentTimeMillis();

        // 5 tentativas falhadas simulando diferentes IPs forjados no cabeçalho X-Forwarded-For
        for (int i = 1; i <= 5; i++) {
            String fakeIpKey = "ip:192.168.1." + i;
            limiter.registarFalha(fakeIpKey, agora);
            limiter.registarFalha(emailKey, agora);
        }

        // O email deve estar bloqueado, independentemente de os IPs serem diferentes
        assertTrue(limiter.estaBloqueado(emailKey, agora), "O email alvo deve estar bloqueado após 5 tentativas");
    }

    @Test
    void aposTempoLimiteLoginVoltaAFuncionar() {
        String chave = "email:usuario@teste.com";
        long t0 = 1_000_000L;

        for (int i = 0; i < 5; i++) {
            limiter.registarFalha(chave, t0);
        }
        assertTrue(limiter.estaBloqueado(chave, t0));

        // 9 minutos depois: ainda bloqueado
        long t9min = t0 + (9 * 60 * 1000L);
        assertTrue(limiter.estaBloqueado(chave, t9min));

        // 10 minutos e 1 segundo depois: desbloqueado
        long t10min1s = t0 + (10 * 60 * 1000L) + 1000L;
        assertFalse(limiter.estaBloqueado(chave, t10min1s));
    }

    @Test
    void entradasExpiradasSaoRemovidasNaLimpeza() {
        long t0 = 1_000_000L;
        limiter.registarFalha("ip:expirado1", t0);
        limiter.registarFalha("email:expirado2@teste.com", t0);

        assertEquals(2, limiter.totalRegistros());

        // Limpeza no momento t0 não remove porque ainda estão na janela
        limiter.limparExpirados(t0 + 1000L);
        assertEquals(2, limiter.totalRegistros());

        // Após expirar a janela de 10 minutos (600.000 ms)
        long tExpirado = t0 + 600_001L;
        limiter.limparExpirados(tExpirado);

        assertEquals(0, limiter.totalRegistros(), "Todas as entradas expiradas devem ser removidas da memória");
    }
}
