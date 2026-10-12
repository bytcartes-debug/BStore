package util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class Fuso {

    public static final ZoneId LOJA = ZoneId.of("Africa/Maputo");
    private static volatile Clock clock = Clock.system(carregarZoneId());

    private Fuso() {
    }

    public static ZoneId zona() {
        return clock.getZone();
    }

    private static ZoneId carregarZoneId() {
        String tz = EnvUtil.get("APP_TIMEZONE", "Africa/Maputo");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return LOJA;
        }
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    public static LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public static Clock getClock() {
        return clock;
    }

    public static synchronized void fixarRelogio(Clock novoClock) {
        clock = novoClock;
    }

    public static synchronized void fixarEm(LocalDateTime dataHora) {
        Instant instant = dataHora.atZone(LOJA).toInstant();
        clock = Clock.fixed(instant, LOJA);
    }

    public static synchronized void reiniciarRelogio() {
        clock = Clock.system(carregarZoneId());
    }
}
