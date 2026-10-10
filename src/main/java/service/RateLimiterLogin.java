package service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterLogin {
    private final int maxTentativas;
    private final long janelaBloqueioMs;
    private final Map<String, Registro> tentativas = new ConcurrentHashMap<>();

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

    public synchronized void resetParaTestes() {
        tentativas.clear();
    }
}
