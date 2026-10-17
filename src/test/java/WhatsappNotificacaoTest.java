import model.*;
import org.junit.jupiter.api.Test;
import service.notificacao.WhatsappNotificacaoService;
import service.notificacao.WhatsappNotificacaoService.ConfiguracaoZapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WhatsappNotificacaoTest {

    @Test
    void testConfiguracaoZapiValidacao() {
        ConfiguracaoZapi vazia = new ConfiguracaoZapi(true, "Loja Teste", "", "", "", "");
        assertFalse(vazia.isConfigurada());

        ConfiguracaoZapi parcial = new ConfiguracaoZapi(true, "Loja Teste", "inst-123", "", "", "258841234567");
        assertFalse(parcial.isConfigurada());

        ConfiguracaoZapi completa = new ConfiguracaoZapi(true, "Loja Teste", "inst-123", "tok-abc", "client-xyz", "258841234567");
        assertTrue(completa.isConfigurada());
        assertEquals("Loja Teste", completa.nomeLoja);
        assertEquals("inst-123", completa.instanceId);
        assertEquals("tok-abc", completa.token);
        assertEquals("258841234567", completa.telefone);
    }

    @Test
    void testLimpezaNumeroTelefone() {
        String telComPontosETracos = "+258 84 123-4567";
        String limpo = telComPontosETracos.replaceAll("[^0-9]", "");
        assertEquals("258841234567", limpo);

        String telBrasil = "+55 (11) 99999-8888";
        String limpoBrasil = telBrasil.replaceAll("[^0-9]", "");
        assertEquals("5511999998888", limpoBrasil);
    }

    @Test
    void testResilienciaSemLancamentoDeExcecao() {
        WhatsappNotificacaoService service = new WhatsappNotificacaoService();

        // Operações assíncronas não devem lançar exceções mesmo com dados nulos ou sem Z-API
        assertDoesNotThrow(() -> service.notificarVendaAsync(null, null, null, null, null, null));
        assertDoesNotThrow(() -> service.notificarBaixoEstoqueAsync(null, null));
        assertDoesNotThrow(() -> service.notificarNovoProdutoAsync(null, null));
        assertDoesNotThrow(() -> service.notificarAberturaCaixaAsync(null, null, null));
        assertDoesNotThrow(() -> service.notificarFechoCaixaAsync(null, null, null, null, null, null));
    }

    @Test
    void testFormatoProdutoStockBaixo() {
        Produto p = new Produto("Refrigerante Coca-Cola 330ml", new BigDecimal("50.00"), new BigDecimal("2.000"), "un", null);
        p.setStockMinimo(new BigDecimal("5.000"));

        assertTrue(p.stockAbaixoMinimo());

        p.setQuantidadeStock(new BigDecimal("10.000"));
        assertFalse(p.stockAbaixoMinimo());
    }
}
