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

    public static String escape(String val) {
        if (val == null) return "";
        String s = val.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
        if (s.contains(";") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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
