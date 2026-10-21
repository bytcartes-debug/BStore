package service;

import dao.RegistroAuditoriaDAO;
import model.RegistroAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final RegistroAuditoriaDAO dao;
    private final ExecutorService executor;

    public AuditoriaService() {
        this(new RegistroAuditoriaDAO());
    }

    public AuditoriaService(RegistroAuditoriaDAO dao) {
        this.dao = dao;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "bstore-auditoria-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void registrarAsync(Long usuarioId, String usuarioNome, String tipoEntidade, String acao, Long entidadeId, String detalhes, String ipOrigem) {
        if (usuarioId == null) return;
        executor.submit(() -> {
            try {
                RegistroAuditoria reg = new RegistroAuditoria(
                    usuarioId,
                    usuarioNome,
                    tipoEntidade,
                    acao,
                    entidadeId,
                    detalhes,
                    ipOrigem
                );
                dao.salvar(reg);
            } catch (Exception e) {
                log.warn("[Auditoria] Falha ao registar log de auditoria ({}/{}): {}", tipoEntidade, acao, e.getMessage());
            }
        });
    }

    public void registrarAsync(Long usuarioId, String usuarioNome, String tipoEntidade, String acao, Long entidadeId, String detalhes, String ipOrigem, String userAgent) {
        String det = detalhes;
        if (userAgent != null && !userAgent.isBlank()) {
            det = (det != null && !det.isBlank()) ? det + " [UA: " + userAgent + "]" : "[UA: " + userAgent + "]";
        }
        registrarAsync(usuarioId, usuarioNome, tipoEntidade, acao, entidadeId, det, ipOrigem);
    }

    public Map<String, Object> listarPaginado(Long usuarioId, String tipoEntidade, String acao, int pagina, int limite) {
        int pag = Math.max(1, pagina);
        int lim = (limite > 0 && limite <= 100) ? limite : 50;
        int offset = (pag - 1) * lim;

        List<RegistroAuditoria> registros = dao.listarComFiltros(usuarioId, tipoEntidade, acao, offset, lim);
        long totalRegistros = dao.contarComFiltros(usuarioId, tipoEntidade, acao);
        int totalPaginas = (int) Math.ceil((double) totalRegistros / lim);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("itens", registros);
        resultado.put("pagina", pag);
        resultado.put("limite", lim);
        resultado.put("totalItens", totalRegistros);
        resultado.put("totalPaginas", totalPaginas);

        return resultado;
    }
}
