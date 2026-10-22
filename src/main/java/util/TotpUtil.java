package util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Utilitário RFC 6238 para Autenticação de Dois Fatores (TOTP - Time-Based One-Time Password).
 * Compatível com Google Authenticator, Microsoft Authenticator, 1Password e Authy.
 */
public class TotpUtil {

    private static final int DIGITOS = 6;
    private static final int PERIODO_SEGUNDOS = 30;
    private static final String ALGORITMO_HMAC = "HmacSHA1";
    private static final String BASE32_ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Gera uma chave secreta aleatória de 160 bits (20 bytes) codificada em Base32.
     */
    public static String gerarSegredo() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Constrói a URI padrão otpauth:// para escaneamento via QR Code.
     */
    public static String gerarUriOtpauth(String email, String secret, String emissor) {
        String label = (emissor != null && !emissor.isBlank() ? emissor : "BStore") + ":" + email;
        String issuer = emissor != null && !emissor.isBlank() ? emissor : "BStore";
        return String.format(
            "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
            URLEncoder.encode(label, StandardCharsets.UTF_8).replace("+", "%20"),
            secret.trim().toUpperCase(),
            URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
        );
    }

    /**
     * Valida o código TOTP fornecido contra o segredo, aceitando margem de 1 intervalo (skew de +/- 30 segundos).
     */
    public static boolean validarCodigo(String secretBase32, String codigoStr) {
        if (secretBase32 == null || codigoStr == null) return false;
        String codigoLimpo = codigoStr.trim().replaceAll("\\s+", "");
        if (codigoLimpo.length() != DIGITOS) return false;

        try {
            int codigoAlvo = Integer.parseInt(codigoLimpo);
            byte[] chave = decodeBase32(secretBase32.trim().toUpperCase());
            long timestampAtual = Instant.now().getEpochSecond();
            long stepAtual = timestampAtual / PERIODO_SEGUNDOS;

            // Verifica janela: passo anterior, passo atual e próximo passo (+/- 30s)
            for (long step = stepAtual - 1; step <= stepAtual + 1; step++) {
                int codigoCalculado = calcularTotp(chave, step);
                if (codigoCalculado == codigoAlvo) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calcula o valor numérico de 6 dígitos para um dado step temporal.
     */
    public static int calcularTotp(byte[] chave, long timeStep) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance(ALGORITMO_HMAC);
        mac.init(new SecretKeySpec(chave, ALGORITMO_HMAC));
        byte[] hash = mac.doFinal(data);

        // Dynamic truncation (RFC 4226 seção 5.4)
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   | (hash[offset + 3] & 0xFF);

        return binary % 1_000_000;
    }

    /**
     * Codificador Base32 puro (RFC 4648).
     */
    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALFABETO.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALFABETO.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    /**
     * Decodificador Base32 puro (RFC 4648).
     */
    public static byte[] decodeBase32(String base32) {
        String s = base32.trim().toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer bb = ByteBuffer.allocate((s.length() * 5) / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : s.toCharArray()) {
            int val = BASE32_ALFABETO.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bb.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }
        return bb.array();
    }
}
