import org.junit.jupiter.api.Test;
import service.ia.IaControloService;
import service.ia.NegocioToolsService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IaPrompt5Test {

    private final IaControloService iaService = new IaControloService();
    private final NegocioToolsService toolsService = new NegocioToolsService();

    @Test
    public void testResumoDiaDescartadoComNumeroInventado() {
        Map<String, Object> metricas = Map.of(
                "vendasHoje", "12500.00",
                "lucroHoje", "3400.00",
                "qtdVendasHoje", 42,
                "ticketMedioHoje", "297.61"
        );

        // Texto que contém apenas números que existem nas métricas
        String textoValido = "Hoje a loja faturou 12500.00 MT com 42 vendas e lucro de 3400.00 MT.";
        assertTrue(iaService.validarNumerosDoTexto(textoValido, metricas),
                "O texto com números reais conhecidos deve ser aceito");

        // Texto com número inventado (ex: 89000.00 MT não presente nas métricas)
        String textoAlucinando = "Hoje a loja faturou 12500.00 MT mas foram perdidos 89000.00 MT em furtos.";
        assertFalse(iaService.validarNumerosDoTexto(textoAlucinando, metricas),
                "O texto com número inventado deve ser rejeitado");
    }

    @Test
    public void testMensagemCobrancaRejeitaAmeacasEValoresIncorretos() {
        String saldo = "1500.00";
        String loja = "Mercearia Central";

        // Mensagem válida
        String valida = "Olá João, lembrete da Mercearia Central: saldo em aberto de 1500.00 MT. Obrigado!";
        assertTrue(iaService.validarMensagemCobranca(valida, saldo, loja));

        // Rejeita linguagem ameaçadora
        String ameacadora = "João, pague os 1500.00 MT na Mercearia Central ou chamaremos a polícia para te prender!";
        assertFalse(iaService.validarMensagemCobranca(ameacadora, saldo, loja));

        // Rejeita valor ausente ou incorreto
        String valorErrado = "Olá João, solicitamos o pagamento de 9999.00 MT na Mercearia Central.";
        assertFalse(iaService.validarMensagemCobranca(valorErrado, saldo, loja));

        // Rejeita se não tiver identificação da loja
        String semLoja = "Olá João, tem um valor de 1500.00 MT pendente.";
        assertFalse(iaService.validarMensagemCobranca(semLoja, saldo, loja));

        // Rejeita se vazar telefone
        String comTelefone = "Olá João, ligue para +258 841234567 para pagar 1500.00 MT na Mercearia Central.";
        assertFalse(iaService.validarMensagemCobranca(comTelefone, saldo, loja));
    }

    @Test
    public void testModelosCobrancaFixosSemIa() {
        List<String> cordial = iaService.gerarModelosCobrancaFixos("Carlos", "2500.00", 3, "BStore", "cordial");
        assertEquals(2, cordial.size());
        assertTrue(cordial.get(0).contains("Carlos"));
        assertTrue(cordial.get(0).contains("2500.00"));
        assertTrue(cordial.get(0).contains("BStore"));

        List<String> firme = iaService.gerarModelosCobrancaFixos("Carlos", "2500.00", 15, "BStore", "firme");
        assertEquals(2, firme.size());
        assertTrue(firme.get(0).contains("15 dias"));

        List<String> curto = iaService.gerarModelosCobrancaFixos("Carlos", "2500.00", 0, "BStore", "curto");
        assertEquals(2, curto.size());
        assertTrue(curto.get(0).length() < 200);
    }

    @Test
    public void testResumoSemIaGeraTextoEstruturado() {
        Map<String, Object> metricas = Map.of(
                "vendasHoje", "5000.00",
                "lucroHoje", "1200.00",
                "qtdVendasHoje", "15",
                "ticketMedioHoje", "333.33",
                "despesasHoje", "200.00"
        );

        String resumo = iaService.gerarResumoSemIa(metricas);
        assertNotNull(resumo);
        assertTrue(resumo.contains("5000.00 MT"));
        assertTrue(resumo.contains("1200.00 MT"));
        assertTrue(resumo.contains("15"));
    }

    @Test
    public void testFerramentasRejeitamNomesInvalidosETratamParametros() {
        Map<String, Object> resInvalido = toolsService.executarFerramenta(1L, "ferramenta_inexistente", Map.of());
        assertTrue(resInvalido.containsKey("erro"));

        Map<String, Object> resNulo = toolsService.executarFerramenta(1L, null, Map.of());
        assertTrue(resNulo.containsKey("erro"));
    }
}
