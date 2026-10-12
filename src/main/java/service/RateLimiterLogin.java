package service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RateLimiterLogin {
    private final int maxTentativas;
    private final long janelaBloqueioMs;
    private final Map<String, Registro> tentativas = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public static class Registro {
        public int falhas;
        public long bloqueadoAte;
        public long primeiraFalhaEm;
    }

    public RateLimiterLogin() {
        this(5, 10 * 60 * 1000L); // 5 tentativas, 10 minutos
    }

    public RateLimiterLogin(int maxTentativas, long janelaBloqueioMs) {
        this.maxTentativas = maxTentativas;
        this.janelaBloqueioMs = janelaBloqueioMs;
        // Agendador com daemon thread para limpeza periódica de entradas expiradas sem reter encerramento da JVM
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiterCleaner");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(
            () -> limparExpirados(System.currentTimeMillis()),
            5, 5, TimeUnit.MINUTES
        );
    }

    public synchronized boolean estaBloqueado(String chave) {
        return estaBloqueado(chave, System.currentTimeMillis());
    }

    public synchronized boolean estaBloqueado(String chave, long agora) {
        Registro reg = tentativas.get(chave);
        if (reg == null) return false;
        if (reg.bloqueadoAte > agora) {
            return true;
        }
        if (reg.bloqueadoAte > 0 && reg.bloqueadoAte <= agora) {
            tentativas.remove(chave);
            return false;
        }
        if (agora - reg.primeiraFalhaEm > janelaBloqueioMs) {
            tentativas.remove(chave);
            return false;
        }
        return false;
    }

    public synchronized void registarFalha(String chave) {
        registarFalha(chave, System.currentTimeMillis());
    }

    public synchronized void registarFalha(String chave, long agora) {
        Registro reg = tentativas.computeIfAbsent(chave, k -> {
            Registro r = new Registro();
            r.primeiraFalhaEm = agora;
            return r;
        });
        reg.falhas++;
        if (reg.falhas >= maxTentativas) {
            reg.bloqueadoAte = agora + janelaBloqueioMs;
        }
    }

    public synchronized void limpar(String chave) {
        tentativas.remove(chave);
    }

    public synchronized void limparExpirados(long agora) {
        Iterator<Map.Entry<String, Registro>> it = tentativas.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Registro> entry = it.next();
            Registro reg = entry.getValue();
            boolean expirouBloqueio = reg.bloqueadoAte > 0 && reg.bloqueadoAte <= agora;
            boolean expirouJanela = reg.bloqueadoAte == 0 && (agora - reg.primeiraFalhaEm > janelaBloqueioMs);
            if (expirouBloqueio || expirouJanela) {
                it.remove();
            }
        }
    }

    public int totalRegistros() {
        return tentativas.size();
    }

    public synchronized void resetParaTestes() {
        tentativas.clear();
    }

    public void fechar() {
        scheduler.shutdownNow();
    }
}
