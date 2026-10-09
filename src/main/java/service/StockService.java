package service;

import dao.MovimentoStockDAO;
import dao.ProdutoDAO;
import model.MovimentoStock;
import model.Produto;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class StockService {

    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final MovimentoStockDAO movimentoStockDAO = new MovimentoStockDAO();

    public MovimentoStock registarMovimento(EntityManager em, Long usuarioId, Long produtoId, String tipo,
                                           BigDecimal quantidade, BigDecimal custoUnitario, String motivo,
                                           String referenciaTipo, Long referenciaId, Long criadoPor) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("Tipo de movimento é obrigatório.");
        }
        String tipoNorm = tipo.toUpperCase();
        if (quantidade == null || quantidade.scale() > 3) {
            throw new IllegalArgumentException("Quantidade deve ter no máximo 3 casas decimais.");
        }
        quantidade = quantidade.setScale(3, RoundingMode.HALF_UP);

        Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, produtoId, usuarioId);
        if (produto == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
        }
        if ("VENDA".equals(tipoNorm) && !produto.isAtivo()) {
            throw new BarracaService.ConflitoException("O produto " + produto.getNome() + " está arquivado e não pode ser vendido.");
        }

        BigDecimal saldoAtual = produto.getQuantidadeStock();
        BigDecimal novoSaldo = saldoAtual.add(quantidade);
        if (novoSaldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new BarracaService.ConflitoException("Stock insuficiente para " + produto.getNome() + ".");
        }

        produto.setQuantidadeStock(novoSaldo);

        BigDecimal custoNorm = custoUnitario != null ? custoUnitario.setScale(2, RoundingMode.HALF_UP) : produto.getCusto();
        MovimentoStock movimento = new MovimentoStock(
                usuarioId, produto, tipoNorm, quantidade, custoNorm,
                motivo != null ? motivo.trim() : null,
                referenciaTipo, referenciaId, criadoPor
        );
        movimentoStockDAO.salvar(em, movimento);
        return movimento;
    }

    public List<MovimentoStock> registarEntradaLote(Long usuarioId, List<Map<String, Object>> itens,
                                                    String motivoGeral, Long criadoPor) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Lista de itens da entrada está vazia.");
        }

        try {
            return JPAUtil.emTransacao(usuarioId, em -> {
                List<MovimentoStock> movimentos = new ArrayList<>();
                for (Map<String, Object> item : itens) {
                    Long produtoId = Long.valueOf(item.get("produtoId").toString());
                    BigDecimal qtd = new BigDecimal(item.get("quantidade").toString()).setScale(3, RoundingMode.HALF_UP);
                    if (qtd.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("A quantidade deve ser positiva.");
                    }

                    BigDecimal custoUnitario = item.get("custoUnitario") != null
                            ? new BigDecimal(item.get("custoUnitario").toString()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    if (custoUnitario.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("O custo unitário não pode ser negativo.");
                    }

                    String motivo = item.get("motivo") != null ? item.get("motivo").toString() : motivoGeral;

                    Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, produtoId, usuarioId);
                    if (produto == null) {
                        throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado: " + produtoId);
                    }

                    // Custo médio ponderado: (saldoAntigo * custoAntigo + qtd * custoNovo) / (saldoAntigo + qtd)
                    BigDecimal saldoAntigo = produto.getQuantidadeStock();
                    BigDecimal custoAntigo = produto.getCusto();
                    BigDecimal novoCusto;
                    if (saldoAntigo.compareTo(BigDecimal.ZERO) <= 0) {
                        novoCusto = custoUnitario;
                    } else {
                        BigDecimal valorAntigo = saldoAntigo.multiply(custoAntigo);
                        BigDecimal valorNovo = qtd.multiply(custoUnitario);
                        BigDecimal totalQtd = saldoAntigo.add(qtd);
                        novoCusto = valorAntigo.add(valorNovo).divide(totalQtd, 2, RoundingMode.HALF_UP);
                    }
                    produto.setCusto(novoCusto);

                    MovimentoStock mov = registarMovimento(em, usuarioId, produtoId, "ENTRADA", qtd,
                            custoUnitario, motivo, "COMPRA", null, criadoPor);
                    movimentos.add(mov);
                }
                return movimentos;
            });
        } catch (PessimisticLockException e) {
            throw new BarracaService.ConflitoException("O stock está a ser atualizado concorrentemente. Tente novamente.");
        }
    }

    public MovimentoStock registarAjuste(Long usuarioId, Long produtoId, BigDecimal novaQuantidade,
                                        BigDecimal diferenca, String tipo, String motivo, Long criadoPor) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("O motivo do ajuste é obrigatório.");
        }

        try {
            return JPAUtil.emTransacao(usuarioId, em -> {
                Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, produtoId, usuarioId);
                if (produto == null) {
                    throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
                }

                BigDecimal qtdMovimento;
                if (novaQuantidade != null) {
                    BigDecimal novaNorm = novaQuantidade.setScale(3, RoundingMode.HALF_UP);
                    if (novaNorm.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("A nova quantidade não pode ser negativa.");
                    }
                    qtdMovimento = novaNorm.subtract(produto.getQuantidadeStock());
                } else if (diferenca != null) {
                    qtdMovimento = diferenca.setScale(3, RoundingMode.HALF_UP);
                } else {
                    throw new IllegalArgumentException("Informe a nova quantidade ou a diferença.");
                }

                if (qtdMovimento.compareTo(BigDecimal.ZERO) == 0) {
                    return null;
                }

                String tipoMov = (tipo != null && !tipo.isBlank()) ? tipo.toUpperCase() :
                        (qtdMovimento.compareTo(BigDecimal.ZERO) < 0 ? "PERDA" : "AJUSTE");

                return registarMovimento(em, usuarioId, produtoId, tipoMov, qtdMovimento,
                        produto.getCusto(), motivo.trim(), null, null, criadoPor);
            });
        } catch (PessimisticLockException e) {
            throw new BarracaService.ConflitoException("O stock está a ser atualizado concorrentemente. Tente novamente.");
        }
    }

    public List<Map<String, Object>> previaContagem(Long usuarioId, List<Map<String, Object>> contagens) {
        if (contagens == null || contagens.isEmpty()) {
            return Collections.emptyList();
        }
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Map<String, Object>> previa = new ArrayList<>();
            for (Map<String, Object> item : contagens) {
                Long produtoId = Long.valueOf(item.get("produtoId").toString());
                BigDecimal contada = new BigDecimal(item.get("quantidadeContada").toString()).setScale(3, RoundingMode.HALF_UP);
                Produto produto = em.find(Produto.class, produtoId);
                if (produto == null || !usuarioId.equals(produto.getUsuarioId())) {
                    continue;
                }
                BigDecimal atual = produto.getQuantidadeStock();
                BigDecimal dif = contada.subtract(atual);
                BigDecimal valorDif = dif.multiply(produto.getCusto()).setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> linha = new LinkedHashMap<>();
                linha.put("produtoId", produto.getId());
                linha.put("nome", produto.getNome());
                linha.put("stockAtual", atual);
                linha.put("quantidadeContada", contada);
                linha.put("diferenca", dif);
                linha.put("valorDiferenca", valorDif);
                previa.add(linha);
            }
            return previa;
        });
    }

    public List<MovimentoStock> confirmarContagem(Long usuarioId, List<Map<String, Object>> contagens, Long criadoPor) {
        if (contagens == null || contagens.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JPAUtil.emTransacao(usuarioId, em -> {
                List<MovimentoStock> movimentos = new ArrayList<>();
                for (Map<String, Object> item : contagens) {
                    Long produtoId = Long.valueOf(item.get("produtoId").toString());
                    BigDecimal contada = new BigDecimal(item.get("quantidadeContada").toString()).setScale(3, RoundingMode.HALF_UP);
                    if (contada.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Quantidade contada não pode ser negativa.");
                    }

                    Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, produtoId, usuarioId);
                    if (produto == null) {
                        throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
                    }

                    BigDecimal dif = contada.subtract(produto.getQuantidadeStock());
                    if (dif.compareTo(BigDecimal.ZERO) != 0) {
                        String tipo = dif.compareTo(BigDecimal.ZERO) < 0 ? "PERDA" : "AJUSTE";
                        MovimentoStock mov = registarMovimento(em, usuarioId, produtoId, tipo, dif,
                                produto.getCusto(), "Contagem de inventário", "INVENTARIO", null, criadoPor);
                        movimentos.add(mov);
                    }
                }
                return movimentos;
            });
        } catch (PessimisticLockException e) {
            throw new BarracaService.ConflitoException("O stock está a ser atualizado concorrentemente. Tente novamente.");
        }
    }

    public boolean verificarIntegridadeStock(Long usuarioId, Long produtoId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Produto p = em.find(Produto.class, produtoId);
            if (p == null || !usuarioId.equals(p.getUsuarioId())) {
                throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
            }
            BigDecimal soma = movimentoStockDAO.somarQuantidadePorProduto(em, usuarioId, produtoId);
            return p.getQuantidadeStock().compareTo(soma) == 0;
        });
    }

    public dao.Pagina<MovimentoStock> listarMovimentosPorProduto(Long usuarioId, Long produtoId, int page, int pageSize) {
        return movimentoStockDAO.listarPorProduto(usuarioId, produtoId, page, pageSize);
    }

    public dao.Pagina<MovimentoStock> listarMovimentos(Long usuarioId, int page, int pageSize, String tipo,
                                                      java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        return movimentoStockDAO.listarMovimentos(usuarioId, page, pageSize, tipo, inicio, fim);
    }

    public BigDecimal calcularValorTotalStockCusto(Long usuarioId) {
        return movimentoStockDAO.calcularValorTotalStockCusto(usuarioId);
    }

    public BigDecimal calcularCustoVendasPeriodo(Long usuarioId, java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        return movimentoStockDAO.calcularCustoVendasPeriodo(usuarioId, inicio, fim);
    }
}
