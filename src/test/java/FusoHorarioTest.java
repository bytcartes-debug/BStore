import model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.Fuso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class FusoHorarioTest {

    @BeforeEach
    void setup() {
        Fuso.reiniciarRelogio();
    }

    @AfterEach
    void tearDown() {
        Fuso.reiniciarRelogio();
    }

    @Test
    void relogioFixadoAs23h30ContaComoDia11() {
        LocalDateTime dt = LocalDateTime.of(2026, 10, 11, 23, 30, 0);
        Fuso.fixarEm(dt);

        assertEquals(dt, Fuso.agora());
        assertEquals(LocalDate.of(2026, 10, 11), Fuso.hoje());

        Venda venda = new Venda();
        assertEquals(LocalDate.of(2026, 10, 11), venda.getDataVenda());
        assertEquals(dt, venda.getCriadaEm());
    }

    @Test
    void relogioFixadoAs00h30ContaComoDia12ENaoComoDia11Utc() {
        LocalDateTime dt = LocalDateTime.of(2026, 10, 12, 0, 30, 0);
        Fuso.fixarEm(dt);

        assertEquals(dt, Fuso.agora());
        assertEquals(LocalDate.of(2026, 10, 12), Fuso.hoje());

        Venda venda = new Venda();
        assertEquals(LocalDate.of(2026, 10, 12), venda.getDataVenda());
        assertEquals(dt, venda.getCriadaEm());
    }

    @Test
    void intervaloDoDiaUsaSempreFusoDaLoja() {
        LocalDateTime dt = LocalDateTime.of(2026, 10, 11, 23, 30, 0);
        Fuso.fixarEm(dt);

        LocalDate hoje = Fuso.hoje();
        assertEquals(LocalDate.of(2026, 10, 11), hoje);

        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDiaExclusive = hoje.plusDays(1).atStartOfDay();

        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 0, 0), inicioDia);
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0, 0), fimDiaExclusive);

        // A venda às 23:30 cai estritamente dentro do intervalo [inicioDia, fimDiaExclusive)
        Venda venda = new Venda();
        assertTrue(!venda.getCriadaEm().isBefore(inicioDia) && venda.getCriadaEm().isBefore(fimDiaExclusive),
            "A venda às 23:30 deve pertencer ao intervalo do dia 11");
    }

    @Test
    void todasEntidadesGravamCriadaEmComMesmoRelogioSemDependerDoBanco() {
        LocalDateTime fixo = LocalDateTime.of(2026, 10, 11, 23, 30, 0);
        Fuso.fixarEm(fixo);

        Venda venda = new Venda();
        venda.prePersist();
        assertEquals(fixo, venda.getCriadaEm());

        DespesaCaixa despesa = new DespesaCaixa();
        despesa.prePersist();
        assertEquals(fixo, despesa.getCriadaEm());

        Devolucao devolucao = new Devolucao();
        devolucao.prePersist();
        assertEquals(fixo, devolucao.getCriadaEm());

        SessaoCaixa sessao = new SessaoCaixa();
        sessao.prePersist();
        assertEquals(fixo, sessao.getAbertaEm());

        MovimentoStock mov = new MovimentoStock();
        mov.prePersist();
        assertEquals(fixo, mov.getCriadoEm());

        PagamentoDivida pag = new PagamentoDivida();
        pag.prePersist();
        assertEquals(fixo, pag.getCriadoEm());

        DefinicaoLoja def = new DefinicaoLoja();
        def.prePersist();
        assertEquals(fixo, def.getAtualizadoEm());

        Devedor devedor = new Devedor();
        devedor.prePersist();
        assertEquals(LocalDate.of(2026, 10, 11), devedor.getData());
    }
}
