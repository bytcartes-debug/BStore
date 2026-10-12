package util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CsvUtil {

    public static final String UTF8_BOM = "\uFEFF";
    public static final String DELIMITER = ";";
    public static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CsvUtil() {}

    /**
     * Neutraliza valores de texto contra injeção de fórmulas CSV/Excel (=, +, -, @, \t, \r).
     * Prefixa com apóstrofo (') para garantir que aplicações de folha de cálculo tratem como texto puro.
     */
    public static String escapeTexto(String val) {
        if (val == null) return "";
        boolean temPrefixoPerigoso = val.startsWith("\t") || val.startsWith("\r") || val.startsWith("\n");
        String s = val.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        if (s.isEmpty()) return "";

        char primeiro = s.charAt(0);
        if (temPrefixoPerigoso || primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@') {
            s = "'" + s;
        }

        if (s.contains(";") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Escapa valores para CSV. Se o valor for puramente numérico ou data (incluindo números negativos),
     * preserva a formatação original sem adicionar apóstrofo. Caso contrário, neutraliza potenciais fórmulas.
     */
    public static String escape(String val) {
        if (val == null) return "";
        String s = val.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        if (isNumeroOuData(s)) {
            if (s.contains(";") || s.contains("\"")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
        return escapeTexto(val);
    }

    public static boolean isNumeroOuData(String s) {
        if (s == null || s.isBlank()) return false;
        // Números inteiros, decimais (com ponto ou vírgula) e percentuais, positivos ou negativos
        if (s.matches("^-?\\d+([.,]\\d+)?%?$")) return true;
        // Datas no formato dd/MM/yyyy ou dd/MM/yyyy HH:mm
        if (s.matches("^\\d{2}/\\d{2}/\\d{4}( \\d{2}:\\d{2})?$")) return true;
        return false;
    }

    public static String formatarMoeda(BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String formatarQuantidade(BigDecimal valor) {
        if (valor == null) return "0,000";
        return valor.setScale(3, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String formatarPercentual(BigDecimal valor) {
        if (valor == null) return "0,00%";
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
    }

    public static String formatarDataHora(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DATA_HORA);
    }

    public static String formatarData(LocalDate d) {
        if (d == null) return "";
        return d.format(DATA);
    }
}
