package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import io.javalin.Javalin;
import io.javalin.http.Context;
import model.DefinicaoLoja;
import service.ia.AiRequisicao;
import service.ia.AiResposta;
import service.ia.IaControloService;

import java.util.Base64;
import java.util.List;
import java.util.Map;

public class IaRoutes {

    private final IaControloService iaControloService;

    public IaRoutes(IaControloService iaControloService) {
        this.iaControloService = iaControloService;
    }

    public void register(Javalin app) {
        app.get("/api/ia/status", this::obterStatus);
        app.put("/api/ia/config", this::atualizarConfig);
        app.post("/api/ia/testar", this::testarLigacao);

        app.post("/api/ia/produto-por-foto", this::analisarProdutoPorFoto);
        app.post("/api/ia/produto-por-texto", this::normalizarProdutoPorTexto);
        app.post("/api/ia/mapear-colunas", this::mapearColunas);
        app.post("/api/ia/fatura-por-foto", this::analisarFaturaPorFoto);
        app.post("/api/ia/resumo-dia", this::gerarResumoDia);
        app.post("/api/ia/sugerir-cobranca", this::sugerirCobranca);
        app.post("/api/ia/perguntar-negocio", this::perguntarNegocio);
    }

    private void obterStatus(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        ctx.json(iaControloService.obterStatus(uid));
    }

    private void atualizarConfig(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        Boolean activa = body.get("activa") != null ? Boolean.valueOf(body.get("activa").toString()) : null;
        String provedor = body.get("provedor") != null ? body.get("provedor").toString() : null;
        String apiKey = body.get("apiKey") != null ? body.get("apiKey").toString() : null;
        Integer limiteDiario = body.get("limiteDiario") != null ? Integer.valueOf(body.get("limiteDiario").toString()) : null;
        Integer limiteMensal = body.get("limiteMensal") != null ? Integer.valueOf(body.get("limiteMensal").toString()) : null;

        DefinicaoLoja def = iaControloService.atualizarConfig(uid, activa, provedor, apiKey, limiteDiario, limiteMensal);
        ctx.json(iaControloService.obterStatus(uid));
    }

    private void testarLigacao(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        AiResposta resposta = iaControloService.executarComControlo(uid, "teste_ligacao", () -> {
            AiRequisicao req = new AiRequisicao("Diga apenas: 'Ligação bem-sucedida ao serviço de Inteligência Artificial da BStore.' em uma linha.");
            return iaControloService.getAiClient().gerarTexto(req, null, null);
        });

        if (resposta.isSucesso()) {
            ctx.json(Map.of("sucesso", true, "mensagem", resposta.getTexto()));
        } else {
            ctx.status(400).json(Map.of("sucesso", false, "erro", resposta.getErro()));
        }
    }

    private byte[] extrairBytesBase64(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Imagem em base64 não fornecida.");
        }
        String clean = raw.trim();
        if (clean.contains(",")) {
            clean = clean.substring(clean.indexOf(",") + 1);
        }
        return Base64.getDecoder().decode(clean);
    }

    private String extrairMimeType(String raw, String defaultMime) {
        if (raw != null && raw.startsWith("data:")) {
            int end = raw.indexOf(";base64");
            if (end > 5) {
                return raw.substring(5, end);
            }
        }
        return defaultMime != null ? defaultMime : "image/jpeg";
    }

    private void analisarProdutoPorFoto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        String b64 = ApiHelpers.textoObrigatorio(body, "imagemBase64");
        String mime = body.get("mimeType") != null ? body.get("mimeType").toString() : extrairMimeType(b64, "image/jpeg");
        byte[] bytes = extrairBytesBase64(b64);

        Map<String, Object> resultado = iaControloService.analisarProdutoPorFoto(uid, bytes, mime);
        ctx.json(resultado);
    }

    private void analisarFaturaPorFoto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        String b64 = ApiHelpers.textoObrigatorio(body, "imagemBase64");
        String mime = body.get("mimeType") != null ? body.get("mimeType").toString() : extrairMimeType(b64, "image/jpeg");
        byte[] bytes = extrairBytesBase64(b64);

        Map<String, Object> resultado = iaControloService.analisarFaturaPorFoto(uid, bytes, mime);
        ctx.json(resultado);
    }

    private void gerarResumoDia(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        boolean forcar = Boolean.TRUE.equals(body.get("forcar"));
        String texto = iaControloService.gerarResumoDia(uid, body, forcar);
        ctx.json(Map.of("resumo", texto));
    }

    private void sugerirCobranca(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        String clienteNome = ApiHelpers.textoObrigatorio(body, "clienteNome");
        String saldoDevedor = ApiHelpers.textoObrigatorio(body, "saldoDevedor");
        String tom = body.get("tom") != null ? body.get("tom").toString() : "cordial";
        Integer diasAtraso = null;
        if (body.get("diasAtraso") != null) {
            try {
                diasAtraso = Integer.valueOf(body.get("diasAtraso").toString());
            } catch (Exception ignored) {}
        }

        Map<String, Object> res = iaControloService.sugerirMensagensCobranca(uid, clienteNome, saldoDevedor, diasAtraso, tom);
        List<String> vars = (List<String>) res.get("variantes");
        String primeiraMsg = (vars != null && !vars.isEmpty()) ? vars.get(0) : "";

        ctx.json(Map.of(
                "variantes", vars != null ? vars : List.of(),
                "mensagem", primeiraMsg,
                "tom", res.getOrDefault("tom", tom),
                "iaUsada", res.getOrDefault("iaUsada", false)
        ));
    }

    private void perguntarNegocio(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        String pergunta = ApiHelpers.textoObrigatorio(body, "pergunta");
        Map<String, Object> resposta = iaControloService.perguntarNegocio(uid, pergunta);
        ctx.json(resposta);
    }

    private void normalizarProdutoPorTexto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        String nome = ApiHelpers.textoObrigatorio(body, "nome");
        List<String> categorias = body.get("categorias") instanceof List ? (List<String>) body.get("categorias") : List.of();

        Map<String, Object> resultado = iaControloService.normalizarProdutoPorTexto(uid, nome, categorias);
        ctx.json(resultado);
    }

    private void mapearColunas(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);

        List<String> cabecalhos = body.get("cabecalhos") instanceof List ? (List<String>) body.get("cabecalhos") : List.of();
        List<List<String>> amostra = body.get("amostra") instanceof List ? (List<List<String>>) body.get("amostra") : List.of();

        Map<String, Object> resultado = iaControloService.mapearColunasImportacao(uid, cabecalhos, amostra);
        ctx.json(resultado);
    }
}
