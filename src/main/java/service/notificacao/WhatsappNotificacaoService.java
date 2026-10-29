package service.notificacao;

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
import util.Fuso;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhatsappNotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappNotificacaoService.class);
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DefinicaoLojaDAO definicaoLojaDAO;
    private final UsuarioDAO usuarioDAO;
    private final NotificacaoWhatsAppClient whatsAppClient;
    private final ExecutorService executor;

    // Controle de taxa do botão de teste: máximo 1 por minuto por usuário
    private final Map<Long, Long> ultimoTestePorUsuario = new ConcurrentHashMap<>();

    public WhatsappNotificacaoService() {
        this(new DefinicaoLojaDAO(), new UsuarioDAO(), new OpenWaClient());
    }

    public WhatsappNotificacaoService(DefinicaoLojaDAO definicaoLojaDAO, UsuarioDAO usuarioDAO) {
        this(definicaoLojaDAO, usuarioDAO, new OpenWaClient());
    }

    public WhatsappNotificacaoService(DefinicaoLojaDAO definicaoLojaDAO, UsuarioDAO usuarioDAO, NotificacaoWhatsAppClient whatsAppClient) {
        this.definicaoLojaDAO = definicaoLojaDAO;
        this.usuarioDAO = usuarioDAO;
        this.whatsAppClient = whatsAppClient;
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "whatsapp-notifier-worker");
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
                ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
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

                whatsAppClient.enviarTexto(cfg.telefone, sb.toString());
            } catch (Exception e) {
                log.error("[OpenWA] Erro ao enviar notificação de venda #{}: {}", venda.getId(), e.getMessage());
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
                ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
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

                whatsAppClient.enviarTexto(cfg.telefone, sb.toString());
            } catch (Exception e) {
                log.error("[OpenWA] Erro ao enviar notificação de baixo estoque: {}", e.getMessage());
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
                ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
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

                whatsAppClient.enviarTexto(cfg.telefone, sb.toString());
            } catch (Exception e) {
                log.error("[OpenWA] Erro ao enviar notificação de novo produto #{}: {}", produto.getId(), e.getMessage());
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
                ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
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

                whatsAppClient.enviarTexto(cfg.telefone, sb.toString());
            } catch (Exception e) {
                log.error("[OpenWA] Erro ao enviar notificação de abertura de caixa: {}", e.getMessage());
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
                ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
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

                whatsAppClient.enviarTexto(cfg.telefone, sb.toString());
            } catch (Exception e) {
                log.error("[OpenWA] Erro ao enviar notificação de fecho de caixa: {}", e.getMessage());
            }
        });
    }

    // =========================================================================
    // TESTE DE CONEXÃO (com rate limit de 1 por minuto por usuário)
    // =========================================================================
    public NotificacaoWhatsAppClient.ResultadoEnvio testarConexao(Long usuarioId) {
        if (usuarioId != null) {
            long agora = System.currentTimeMillis();
            Long ultimo = ultimoTestePorUsuario.get(usuarioId);
            if (ultimo != null && (agora - ultimo) < 60_000L) {
                long segundosRestantes = 60 - ((agora - ultimo) / 1000);
                return new NotificacaoWhatsAppClient.ResultadoEnvio(
                        false,
                        "Aguarde " + segundosRestantes + " segundos antes de testar novamente.",
                        NotificacaoWhatsAppClient.StatusSessao.CONECTADO
                );
            }
            ultimoTestePorUsuario.put(usuarioId, agora);
        }

        ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
        if (!cfg.isConfigurada()) {
            return new NotificacaoWhatsAppClient.ResultadoEnvio(
                    false,
                    "Telefone do dono não configurado nas definições da loja.",
                    NotificacaoWhatsAppClient.StatusSessao.NAO_CONFIGURADO
            );
        }

        String msg = "Teste do BStore";
        return whatsAppClient.enviarTextoDetalhado(cfg.telefone, msg);
    }

    // =========================================================================
    // 6. RESUMO EXECUTIVO DIÁRIO (WHATSAPP DO DONO)
    // =========================================================================
    public NotificacaoWhatsAppClient.ResultadoEnvio notificarResumoDiario(java.time.LocalDate data, Map<String, Object> dadosFecho, Long usuarioId) {
        ConfiguracaoWhatsApp cfg = carregarConfiguracao(usuarioId);
        if (!cfg.isConfigurada()) {
            return new NotificacaoWhatsAppClient.ResultadoEnvio(
                    false,
                    "Telefone do proprietário não configurado nas definições.",
                    NotificacaoWhatsAppClient.StatusSessao.NAO_CONFIGURADO
            );
        }

        java.time.LocalDate dia = data != null ? data : util.Fuso.hoje();
        String totalVendido = dadosFecho != null && dadosFecho.get("totalVendido") != null ? dadosFecho.get("totalVendido").toString() : "0,00";
        String lucroEstimado = dadosFecho != null && dadosFecho.get("lucroEstimado") != null ? dadosFecho.get("lucroEstimado").toString() : "0,00";
        String totalDespesas = dadosFecho != null && dadosFecho.get("totalDespesas") != null ? dadosFecho.get("totalDespesas").toString() : "0,00";
        String totalFiado = dadosFecho != null && dadosFecho.get("totalFiado") != null ? dadosFecho.get("totalFiado").toString() : "0,00";
        Object numVendasObj = dadosFecho != null ? dadosFecho.get("numeroVendas") : "0";
        String numVendas = numVendasObj != null ? numVendasObj.toString() : "0";

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *RESUMO EXECUTIVO DO DIA*\n");
        sb.append("🏪 *Loja:* ").append(cfg.nomeLoja).append("\n");
        sb.append("📅 *Data:* ").append(dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
        sb.append("🕒 *Gerado às:* ").append(formatarDataHora(Fuso.agora())).append("\n");
        sb.append("───────────────────────────\n");
        sb.append("💰 *Faturamento Total:* *").append(totalVendido).append(" MZN*\n");
        sb.append("📈 *Lucro Bruto Estimado:* *").append(lucroEstimado).append(" MZN*\n");
        sb.append("🔢 *Vendas Concluídas:* ").append(numVendas).append("\n");
        if (!"0,00".equals(totalDespesas) && !"0.00".equals(totalDespesas) && !"0".equals(totalDespesas)) {
            sb.append("💸 *Despesas / Saídas:* -").append(totalDespesas).append(" MZN\n");
        }
        if (!"0,00".equals(totalFiado) && !"0.00".equals(totalFiado) && !"0".equals(totalFiado)) {
            sb.append("📝 *Fiado Registado:* ").append(totalFiado).append(" MZN\n");
        }

        if (dadosFecho != null && dadosFecho.get("totaisPorMetodo") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> metodos = (Map<String, String>) dadosFecho.get("totaisPorMetodo");
            if (!metodos.isEmpty()) {
                sb.append("───────────────────────────\n");
                sb.append("💳 *Entradas por Meio de Pagamento:*\n");
                metodos.forEach((k, v) -> {
                    if (!"0,00".equals(v) && !"0.00".equals(v) && !"0".equals(v)) {
                        sb.append(" • ").append(k).append(": ").append(v).append(" MZN\n");
                    }
                });
            }
        }

        sb.append("───────────────────────────\n");
        sb.append("✅ Relatório consolidado Flex Stock / BStore");

        return whatsAppClient.enviarTextoDetalhado(cfg.telefone, sb.toString());
    }

    // =========================================================================
    // 7. COBRANÇA DE FIADO / LEMBRETE DE DÍVIDA (PARA CLIENTE)
    // =========================================================================
    public NotificacaoWhatsAppClient.ResultadoEnvio enviarCobrancaCliente(String telefoneCliente, String mensagem, Long usuarioId) {
        if (telefoneCliente == null || telefoneCliente.isBlank()) {
            return new NotificacaoWhatsAppClient.ResultadoEnvio(
                    false,
                    "Número de telefone do cliente ausente ou inválido.",
                    NotificacaoWhatsAppClient.StatusSessao.NAO_CONFIGURADO
            );
        }
        if (mensagem == null || mensagem.isBlank()) {
            return new NotificacaoWhatsAppClient.ResultadoEnvio(
                    false,
                    "Texto da cobrança não pode estar vazio.",
                    NotificacaoWhatsAppClient.StatusSessao.NAO_CONFIGURADO
            );
        }
        return whatsAppClient.enviarTextoDetalhado(telefoneCliente, mensagem);
    }

    public NotificacaoWhatsAppClient.StatusSessao verificarStatusSessao() {
        return whatsAppClient.verificarStatusSessao();
    }

    public ConfiguracaoWhatsApp carregarConfiguracao(Long usuarioId) {
        DefinicaoLoja def = null;
        if (usuarioId != null) {
            try {
                def = definicaoLojaDAO.obter(usuarioId);
            } catch (Exception ignored) {}
        }

        String nomeLoja = "BStore";
        if (def != null && def.getNomeLoja() != null && !def.getNomeLoja().isBlank()) {
            nomeLoja = def.getNomeLoja().trim();
        } else if (usuarioId != null) {
            try {
                Usuario u = usuarioDAO.buscarPorId(usuarioId);
                if (u != null && u.getNome() != null && !u.getNome().isBlank()) {
                    nomeLoja = u.getNome().trim();
                }
            } catch (Exception ignored) {}
        }

        boolean activa = whatsAppClient.isAtivo();
        if (def != null && def.getWhatsappNotificacoesActivas() != null) {
            activa = def.getWhatsappNotificacoesActivas();
        }

        String telefone = (def != null && def.getWhatsappTelefoneDono() != null && !def.getWhatsappTelefoneDono().isBlank())
                ? def.getWhatsappTelefoneDono().trim()
                : whatsAppClient.getTelefoneDono();

        return new ConfiguracaoWhatsApp(activa, nomeLoja, telefone);
    }

    private String formatarDataHora(LocalDateTime ldt) {
        if (ldt == null) return "";
        return ldt.format(FORMATO_DATA_HORA);
    }

    public static class ConfiguracaoWhatsApp {
        public final boolean activa;
        public final String nomeLoja;
        public final String telefone;

        public ConfiguracaoWhatsApp(boolean activa, String nomeLoja, String telefone) {
            this.activa = activa;
            this.nomeLoja = nomeLoja;
            this.telefone = telefone;
        }

        public boolean isConfigurada() {
            return telefone != null && !telefone.isBlank() && telefone.replaceAll("[^0-9]", "").length() >= 8;
        }
    }
}
