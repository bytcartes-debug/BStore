package service.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import dao.DefinicaoLojaDAO;
import dao.UsuarioDAO;
import model.DefinicaoLoja;
import model.ItemVenda;
import model.PagamentoVenda;
import model.Produto;
import model.SessaoCaixa;
import model.Usuario;
import model.Venda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CsvUtil;
import util.EnvUtil;
import util.Fuso;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhatsappNotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappNotificacaoService.class);
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DefinicaoLojaDAO definicaoLojaDAO;
    private final UsuarioDAO usuarioDAO;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public WhatsappNotificacaoService() {
        this(new DefinicaoLojaDAO(), new UsuarioDAO());
    }

    public WhatsappNotificacaoService(DefinicaoLojaDAO definicaoLojaDAO, UsuarioDAO usuarioDAO) {
        this.definicaoLojaDAO = definicaoLojaDAO;
        this.usuarioDAO = usuarioDAO;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "zapi-whatsapp-notifier");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // 1. VENDA REGISTADA
    // =========================================================================
    public void notificarVendaAsync(Venda venda, List<ItemVenda> itens, List<PagamentoVenda> pagamentos,
                                   BigDecimal troco, String nomeOperador, Long usuarioId) {
        if (venda == null || usuarioId == null) return;
        executor.submit(() -> {
            try {
                ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
                if (!cfg.activa || !cfg.isConfigurada()) return;

                StringBuilder sb = new StringBuilder();
                sb.append("🛒 *NOVA VENDA REGISTADA*\n");
                sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
                sb.append("🔢 *Venda #:* ").append(venda.getNumero() != null ? venda.getNumero() : venda.getId()).append("\n");
                sb.append("📅 *Data/Hora:* ").append(formatarDataHora(venda.getCriadaEm())).append("\n");
                if (nomeOperador != null && !nomeOperador.isBlank()) {
                    sb.append("👤 *Atendido por:* ").append(nomeOperador.trim()).append("\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("📦 *Itens Vendidos:*\n");

                List<ItemVenda> itensParaExibir = (itens != null && !itens.isEmpty()) ? itens : venda.getItens();
                if (itensParaExibir != null && !itensParaExibir.isEmpty()) {
                    for (ItemVenda iv : itensParaExibir) {
                        String nomeProd = (iv.getProduto() != null) ? iv.getProduto().getNome() : "Item";
                        String un = (iv.getProduto() != null && iv.getProduto().getUnidade() != null) ? iv.getProduto().getUnidade() : "un";
                        BigDecimal qtd = iv.getQuantidade() != null ? iv.getQuantidade() : BigDecimal.ONE;
                        BigDecimal subtotal = (iv.getPrecoFinal() != null) ? iv.getPrecoFinal() : BigDecimal.ZERO;

                        sb.append(" • ").append(CsvUtil.formatarQuantidade(qtd)).append(" ").append(un)
                          .append(" x ").append(nomeProd)
                          .append(" = *").append(CsvUtil.formatarMoeda(subtotal)).append(" MZN*\n");
                    }
                } else {
                    sb.append(" • Venda registada\n");
                }

                sb.append("───────────────────────────\n");
                sb.append("💰 *Total:* *").append(CsvUtil.formatarMoeda(venda.getTotal())).append(" MZN*\n");

                List<PagamentoVenda> pgs = (pagamentos != null && !pagamentos.isEmpty()) ? pagamentos : venda.getPagamentos();
                if (pgs != null && !pgs.isEmpty()) {
                    sb.append("💳 *Pagamento:*\n");
                    for (PagamentoVenda pv : pgs) {
                        String mNome = (pv.getMetodoPagamento() != null) ? pv.getMetodoPagamento().getNome() : pv.getMetodo();
                        sb.append("   • ").append(mNome != null ? mNome : "Geral").append(": ")
                          .append(CsvUtil.formatarMoeda(pv.getValor())).append(" MZN");
                        if (pv.getReferencia() != null && !pv.getReferencia().isBlank()) {
                            sb.append(" (ref: ").append(pv.getReferencia().trim()).append(")");
                        }
                        sb.append("\n");
                    }
                }

                if (troco != null && troco.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("💵 *Troco:* ").append(CsvUtil.formatarMoeda(troco)).append(" MZN\n");
                }
                if (venda.getObservacao() != null && !venda.getObservacao().isBlank()) {
                    sb.append("📝 *Obs:* ").append(venda.getObservacao().trim()).append("\n");
                }
                sb.append("───────────────────────────");

                enviarMensagem(cfg, sb.toString());
            } catch (Exception e) {
                log.error("[Z-API] Erro ao enviar notificação de venda #{}: {}", venda.getId(), e.getMessage());
            }
        });
    }

    // =========================================================================
    // 2. BAIXO ESTOQUE / ESTOQUE CRÍTICO
    // =========================================================================
    public void notificarBaixoEstoqueAsync(List<Produto> produtos, Long usuarioId) {
        if (produtos == null || produtos.isEmpty() || usuarioId == null) return;
        executor.submit(() -> {
            try {
                ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
                if (!cfg.activa || !cfg.isConfigurada()) return;

                StringBuilder sb = new StringBuilder();
                sb.append("⚠️ *ALERTA: ESTOQUE BAIXO / CRÍTICO*\n");
                sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
                sb.append("📅 *Data/Hora:* ").append(formatarDataHora(Fuso.agora())).append("\n\n");
                sb.append("Os seguintes produtos atingiram ou ultrapassaram o estoque mínimo:\n\n");

                for (Produto p : produtos) {
                    String un = p.getUnidade() != null ? p.getUnidade() : "un";
                    sb.append("📦 *").append(p.getNome()).append("*\n");
                    sb.append("   • Saldo Atual: *").append(CsvUtil.formatarQuantidade(p.getQuantidadeStock())).append(" ").append(un).append("*\n");
                    sb.append("   • Estoque Mínimo: ").append(CsvUtil.formatarQuantidade(p.getStockMinimo())).append(" ").append(un).append("\n");
                    sb.append("   • Preço Venda: ").append(CsvUtil.formatarMoeda(p.getPreco())).append(" MZN\n\n");
                }

                sb.append("🚨 *Atenção:* Recomenda-se reposição imediata junto ao fornecedor.");

                enviarMensagem(cfg, sb.toString());
            } catch (Exception e) {
                log.error("[Z-API] Erro ao enviar notificação de baixo estoque: {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // 3. ADIÇÃO DE NOVO PRODUTO
    // =========================================================================
    public void notificarNovoProdutoAsync(Produto produto, Long usuarioId) {
        if (produto == null || usuarioId == null) return;
        executor.submit(() -> {
            try {
                ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
                if (!cfg.activa || !cfg.isConfigurada()) return;

                String categoriaNome = (produto.getCategoria() != null) ? produto.getCategoria().getNome() : "Sem categoria";
                String un = produto.getUnidade() != null ? produto.getUnidade() : "un";
                String codBarras = (produto.getCodigoBarras() != null && !produto.getCodigoBarras().isBlank())
                        ? produto.getCodigoBarras().trim() : "Sem código";

                StringBuilder sb = new StringBuilder();
                sb.append("✨ *NOVO PRODUTO CADASTRADO*\n");
                sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
                sb.append("📅 *Data/Hora:* ").append(formatarDataHora(Fuso.agora())).append("\n\n");
                sb.append("📦 *Produto:* *").append(produto.getNome()).append("*\n");
                sb.append("📂 *Categoria:* ").append(categoriaNome).append("\n");
                sb.append("💰 *Preço de Venda:* *").append(CsvUtil.formatarMoeda(produto.getPreco())).append(" MZN*\n");
                if (produto.getCusto() != null && produto.getCusto().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("🏷️ *Preço de Custo:* ").append(CsvUtil.formatarMoeda(produto.getCusto())).append(" MZN\n");
                }
                sb.append("📊 *Estoque Inicial:* *").append(CsvUtil.formatarQuantidade(produto.getQuantidadeStock())).append(" ").append(un).append("*\n");
                sb.append("🔔 *Alerta Estoque Mínimo:* ").append(CsvUtil.formatarQuantidade(produto.getStockMinimo())).append(" ").append(un).append("\n");
                sb.append("🔖 *Código de Barras:* ").append(codBarras).append("\n\n");
                sb.append("✅ Produto cadastrado e pronto para venda.");

                enviarMensagem(cfg, sb.toString());
            } catch (Exception e) {
                log.error("[Z-API] Erro ao enviar notificação de novo produto #{}: {}", produto.getId(), e.getMessage());
            }
        });
    }

    // =========================================================================
    // 4. ABERTURA DE CAIXA
    // =========================================================================
    public void notificarAberturaCaixaAsync(SessaoCaixa sessao, String nomeOperador, Long usuarioId) {
        if (sessao == null || usuarioId == null) return;
        executor.submit(() -> {
            try {
                ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
                if (!cfg.activa || !cfg.isConfigurada()) return;

                StringBuilder sb = new StringBuilder();
                sb.append("🟢 *ABERTURA DE CAIXA*\n");
                sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
                sb.append("📅 *Data/Hora:* ").append(formatarDataHora(sessao.getAbertaEm())).append("\n");
                if (nomeOperador != null && !nomeOperador.isBlank()) {
                    sb.append("👤 *Operador:* ").append(nomeOperador.trim()).append("\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("💵 *Fundo de Abertura:* *").append(CsvUtil.formatarMoeda(sessao.getValorInicial())).append(" MZN*\n");
                if (sessao.getNotaAbertura() != null && !sessao.getNotaAbertura().isBlank()) {
                    sb.append("📝 *Observação:* ").append(sessao.getNotaAbertura().trim()).append("\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("✅ Sessão iniciada e pronta para registrar transações.");

                enviarMensagem(cfg, sb.toString());
            } catch (Exception e) {
                log.error("[Z-API] Erro ao enviar notificação de abertura de caixa: {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // 5. FECHO DE CAIXA
    // =========================================================================
    public void notificarFechoCaixaAsync(SessaoCaixa sessao, Long totalVendas, BigDecimal vendasDinheiro,
                                        BigDecimal despesasDinheiro, String nomeOperador, Long usuarioId) {
        if (sessao == null || usuarioId == null) return;
        executor.submit(() -> {
            try {
                ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
                if (!cfg.activa || !cfg.isConfigurada()) return;

                BigDecimal dif = sessao.getDiferenca() != null ? sessao.getDiferenca() : BigDecimal.ZERO;
                String statusDiferenca;
                if (dif.compareTo(BigDecimal.ZERO) == 0) {
                    statusDiferenca = "✅ Conferido (sem diferença)";
                } else if (dif.compareTo(BigDecimal.ZERO) > 0) {
                    statusDiferenca = "📈 Sobra de caixa (+ " + CsvUtil.formatarMoeda(dif) + " MZN)";
                } else {
                    statusDiferenca = "📉 Quebra / Falta de caixa (" + CsvUtil.formatarMoeda(dif) + " MZN)";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("🔴 *RELATÓRIO DE FECHO DE CAIXA*\n");
                sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
                sb.append("📅 *Fechado em:* ").append(formatarDataHora(sessao.getFechadaEm() != null ? sessao.getFechadaEm() : Fuso.agora())).append("\n");
                sb.append("🕒 *Aberto em:* ").append(formatarDataHora(sessao.getAbertaEm())).append("\n");
                if (nomeOperador != null && !nomeOperador.isBlank()) {
                    sb.append("👤 *Operador:* ").append(nomeOperador.trim()).append("\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("📊 *Movimentação de Dinheiro:*\n");
                sb.append(" • Fundo Inicial: ").append(CsvUtil.formatarMoeda(sessao.getValorInicial())).append(" MZN\n");
                if (vendasDinheiro != null) {
                    sb.append(" • Vendas Dinheiro: +").append(CsvUtil.formatarMoeda(vendasDinheiro)).append(" MZN\n");
                }
                if (despesasDinheiro != null && despesasDinheiro.compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" • Saídas / Despesas: -").append(CsvUtil.formatarMoeda(despesasDinheiro)).append(" MZN\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("🎯 *Valor Esperado:* *").append(CsvUtil.formatarMoeda(sessao.getValorEsperado())).append(" MZN*\n");
                sb.append("📥 *Valor Contado:* *").append(CsvUtil.formatarMoeda(sessao.getValorContado())).append(" MZN*\n");
                sb.append("⚖️ *Diferença:* ").append(statusDiferenca).append("\n");
                if (totalVendas != null) {
                    sb.append("🔢 *Vendas no Turno:* ").append(totalVendas).append("\n");
                }
                if (sessao.getNotaFecho() != null && !sessao.getNotaFecho().isBlank()) {
                    sb.append("📝 *Nota de Fecho:* ").append(sessao.getNotaFecho().trim()).append("\n");
                }
                sb.append("───────────────────────────\n");
                sb.append("🔒 Turno de caixa encerrado com sucesso.");

                enviarMensagem(cfg, sb.toString());
            } catch (Exception e) {
                log.error("[Z-API] Erro ao enviar notificação de fecho de caixa: {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // TESTE DE CONEXÃO
    // =========================================================================
    public boolean testarConexao(Long usuarioId) throws Exception {
        ConfiguracaoZapi cfg = carregarConfiguracao(usuarioId);
        if (!cfg.isConfigurada()) {
            throw new IllegalArgumentException("Z-API não configurada. Preencha a Instância, Token e Telefone do Dono.");
        }

        String msg = "✅ *BStore — Teste de Notificações WhatsApp*\n\n"
                   + "Olá! A integração do *BStore (Flex Stock)* com a sua conta *Z-API* está conectada com sucesso!\n\n"
                   + "Você passará a receber automaticamente:\n"
                   + "🛒 Relatório de cada venda registada\n"
                   + "✨ Alerta de novos produtos cadastrados\n"
                   + "⚠️ Alertas imediatos de baixo estoque\n"
                   + "🟢 Abertura e 🔴 Fecho de caixa em tempo real\n\n"
                   + "📅 Data do teste: " + formatarDataHora(Fuso.agora());

        return enviarMensagem(cfg, msg);
    }

    // =========================================================================
    // ENVIO VIA HTTP Z-API
    // =========================================================================
    private boolean enviarMensagem(ConfiguracaoZapi cfg, String texto) {
        try {
            String telefoneLimpo = cfg.telefone.replaceAll("[^0-9]", "");
            if (telefoneLimpo.isBlank()) {
                log.warn("[Z-API] Número de telefone inválido após limpeza: {}", cfg.telefone);
                return false;
            }

            String endpoint = String.format("https://api.z-api.io/instances/%s/token/%s/send-text",
                    cfg.instanceId.trim(), cfg.token.trim());

            Map<String, String> payload = new HashMap<>();
            payload.put("phone", telefoneLimpo);
            payload.put("message", texto);

            String jsonCorpo = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonCorpo, StandardCharsets.UTF_8));

            if (cfg.clientToken != null && !cfg.clientToken.isBlank()) {
                reqBuilder.header("Client-Token", cfg.clientToken.trim());
            }

            HttpResponse<String> resposta = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                log.info("[Z-API] Notificação enviada com sucesso para {} (Status {})", telefoneLimpo, resposta.statusCode());
                return true;
            } else {
                log.warn("[Z-API] Falha no envio para {}: HTTP {} - {}", telefoneLimpo, resposta.statusCode(), resposta.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[Z-API] Erro ao comunicar com endpoint Z-API: {}", e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // RESOLUÇÃO DE CONFIGURAÇÃO (Prioridade: BD -> .env)
    // =========================================================================
    public ConfiguracaoZapi carregarConfiguracao(Long usuarioId) {
        DefinicaoLoja def = null;
        try {
            def = definicaoLojaDAO.obter(usuarioId);
        } catch (Exception ignored) {}

        String nomeLoja = "BStore";
        if (def != null && def.getNomeLoja() != null && !def.getNomeLoja().isBlank()) {
            nomeLoja = def.getNomeLoja().trim();
        } else {
            Usuario u = usuarioDAO.buscarPorId(usuarioId);
            if (u != null && u.getNome() != null && !u.getNome().isBlank()) {
                nomeLoja = u.getNome().trim();
            }
        }

        boolean activa = true;
        if (def != null && def.getWhatsappNotificacoesActivas() != null) {
            activa = def.getWhatsappNotificacoesActivas();
        } else {
            String envAtiva = EnvUtil.get("ZAPI_NOTIFICACOES_ACTIVAS", "true");
            activa = Boolean.parseBoolean(envAtiva);
        }

        String instanceId = (def != null && def.getZapiInstanceId() != null && !def.getZapiInstanceId().isBlank())
                ? def.getZapiInstanceId().trim()
                : EnvUtil.get("ZAPI_INSTANCE_ID");

        String token = (def != null && def.getZapiToken() != null && !def.getZapiToken().isBlank())
                ? def.getZapiToken().trim()
                : EnvUtil.get("ZAPI_TOKEN");

        String clientToken = (def != null && def.getZapiClientToken() != null && !def.getZapiClientToken().isBlank())
                ? def.getZapiClientToken().trim()
                : EnvUtil.get("ZAPI_CLIENT_TOKEN");

        String telefone = (def != null && def.getWhatsappTelefoneDono() != null && !def.getWhatsappTelefoneDono().isBlank())
                ? def.getWhatsappTelefoneDono().trim()
                : EnvUtil.get("ZAPI_DONO_TELEFONE", EnvUtil.get("WHATSAPP_DONO_TELEFONE"));

        return new ConfiguracaoZapi(activa, nomeLoja, instanceId, token, clientToken, telefone);
    }

    private String formatarDataHora(LocalDateTime ldt) {
        if (ldt == null) return "";
        return ldt.format(FORMATO_DATA_HORA);
    }

    public static class ConfiguracaoZapi {
        public final boolean activa;
        public final String nomeLoja;
        public final String instanceId;
        public final String token;
        public final String clientToken;
        public final String telefone;

        public ConfiguracaoZapi(boolean activa, String nomeLoja, String instanceId, String token,
                                String clientToken, String telefone) {
            this.activa = activa;
            this.nomeLoja = nomeLoja;
            this.instanceId = instanceId;
            this.token = token;
            this.clientToken = clientToken;
            this.telefone = telefone;
        }

        public boolean isConfigurada() {
            return instanceId != null && !instanceId.isBlank()
                    && token != null && !token.isBlank()
                    && telefone != null && !telefone.isBlank();
        }
    }
}
