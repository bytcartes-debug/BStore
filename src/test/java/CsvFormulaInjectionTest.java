import org.junit.jupiter.api.Test;
import util.CsvUtil;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CsvFormulaInjectionTest {

    @Test
    void neutralizaFormulasEmCamposDeTexto() {
        // Fórmula perigosa
        String formula = "=HYPERLINK(\"http://x\",\"a\")";
        String escapado = CsvUtil.escapeTexto(formula);
        assertTrue(escapado.startsWith("'=") || escapado.startsWith("\"'="),
            "Fórmula com = deve ser prefixada com apóstrofo");

        // Outros prefixos de fórmula: +, -, @, \t, \r
        assertTrue(CsvUtil.escapeTexto("+cmd|' /C calc'!A0").contains("'+cmd"), "Prefixo + deve ser neutralizado");
        assertTrue(CsvUtil.escapeTexto("-cmd|' /C calc'!A0").contains("'-cmd"), "Prefixo - de texto deve ser neutralizado");
        assertTrue(CsvUtil.escapeTexto("@SUM(1+1)").contains("'@SUM"), "Prefixo @ deve ser neutralizado");
        assertTrue(CsvUtil.escapeTexto("\t=1+1").contains("'"), "Prefixo tabulação deve ser neutralizado");
        assertTrue(CsvUtil.escapeTexto("\r=1+1").contains("'"), "Prefixo retorno de carro deve ser neutralizado");
    }

    @Test
    void preservaNumerosNegativosEDatasSemApostrofo() {
        BigDecimal negativo = new BigDecimal("-50.00");
        String formatadoMoeda = CsvUtil.formatarMoeda(negativo);
        assertEquals("-50,00", formatadoMoeda);
        assertFalse(formatadoMoeda.startsWith("'"), "Número monetário negativo não pode ter apóstrofo");

        BigDecimal qtdNegativa = new BigDecimal("-3.500");
        String formatadoQtd = CsvUtil.formatarQuantidade(qtdNegativa);
        assertEquals("-3,500", formatadoQtd);
        assertFalse(formatadoQtd.startsWith("'"), "Quantidade negativa não pode ter apóstrofo");

        String data = CsvUtil.formatarData(java.time.LocalDate.of(2026, 10, 11));
        assertEquals("11/10/2026", data);
        assertFalse(data.startsWith("'"), "Data não pode ter apóstrofo");
    }

    @Test
    void escapeGenericoNeutralizaFormulaEPreservaNumeroNegativo() {
        String formula = "=HYPERLINK(\"http://x\",\"a\")";
        String escapadoFormula = CsvUtil.escape(formula);
        assertTrue(escapadoFormula.contains("'="), "CsvUtil.escape() deve neutralizar texto de fórmula");

        String numNegativo = "-50,00";
        String escapadoNum = CsvUtil.escape(numNegativo);
        assertEquals("-50,00", escapadoNum, "CsvUtil.escape() não deve alterar número negativo legítimo");
    }
}
