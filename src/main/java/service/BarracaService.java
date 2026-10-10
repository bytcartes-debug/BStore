package service;

import dao.CategoriaDAO;
import dao.DefinicaoLojaDAO;
import dao.DevedorDAO;
import dao.DespesaCaixaDAO;
import dao.DevolucaoDAO;
import dao.ItemVendaDAO;
import dao.MovimentoStockDAO;
import dao.MetodoPagamentoDAO;
import dao.PagamentoDividaDAO;
import dao.PagamentoVendaDAO;
import dao.ProdutoDAO;
import dao.SessaoCaixaDAO;
import dao.VendaDAO;
import model.Categoria;
import model.DefinicaoLoja;
import model.DespesaCaixa;
import model.Devedor;
import model.Devolucao;
import model.ItemDevolucao;
import model.ItemVenda;
import model.MetodoPagamento;
import model.MovimentoStock;
import model.PagamentoDivida;
import model.PagamentoVenda;
import model.Produto;
import model.SessaoCaixa;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class BarracaService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final VendaDAO vendaDAO = new VendaDAO();
    private final ItemVendaDAO itemVendaDAO = new ItemVendaDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final PagamentoVendaDAO pagamentoVendaDAO = new PagamentoVendaDAO();
    private final PagamentoDividaDAO pagamentoDividaDAO = new PagamentoDividaDAO();
    private final MetodoPagamentoDAO metodoPagamentoDAO = new MetodoPagamentoDAO();
    private final DefinicaoLojaDAO definicaoLojaDAO = new DefinicaoLojaDAO();
    private final SessaoCaixaDAO sessaoCaixaDAO = new SessaoCaixaDAO();
    private final DevolucaoDAO devolucaoDAO = new DevolucaoDAO();
    private final DespesaCaixaDAO despesaCaixaDAO = new DespesaCaixaDAO();

    public Categoria criarCategoria(String nome, String descricao, Long usuarioId) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        }
        Categoria categoria = new Categoria(nome.trim(), descricao);
        categoria.setUsuarioId(usuarioId);
        return categoriaDAO.salvar(categoria);
    }

    public Categoria actualizarCategoria(Categoria categoria, Long usuarioId) {
        if (categoria == null || !usuarioId.equals(categoria.getUsuarioId())) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        if (categoria.getNome() == null || categoria.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        }
        return categoriaDAO.actualizar(categoria);
    }

    public List<Produto> listarProdutosPorCategoria(Long categoriaId, Long usuarioId) {
        return produtoDAO.buscarPorCategoria(categoriaId, usuarioId);
    }

    public void eliminarCategoria(Long id, Long usuarioId) {
        Categoria categoria = categoriaDAO.buscarPorId(id, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        if (categoriaDAO.temProdutos(id, usuarioId)) {
            throw new IllegalStateException("Não é possível eliminar uma categoria com produtos associados.");
        }
        categoriaDAO.eliminar(id);
    }

    public List<Categoria> listarCategorias(Long usuarioId) {
        return categoriaDAO.listarOrdenado(usuarioId);
    }

    public Categoria buscarCategoria(Long id, Long usuarioId) {
        return categoriaDAO.buscarPorId(id, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return criarProduto(nome, preco, BigDecimal.ZERO, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return criarProduto(nome, preco, custo, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        validarProduto(nome, preco, stock);
        BigDecimal custoNorm = moeda(custo != null ? custo : BigDecimal.ZERO);
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, null)) {
            throw new ConflitoException("Já existe um produto com este código de barras.");
        }
        BigDecimal stockNorm = quantidade(stock);
        BigDecimal stockMaxNorm = stockMaximo != null ? quantidade(stockMaximo) : null;
        return JPAUtil.emTransacao(usuarioId, em -> {
            Produto produto = new Produto(nome.trim(), moeda(preco), stockNorm, unidade, categoria);
            produto.setCusto(custoNorm);
            produto.setAtivo(true);
            produto.setStockMinimo(stockMinimo(stockMinimo));
            produto.setStockMaximo(stockMaxNorm);
            produto.setCodigoBarras(codigoNormalizado);
            produto.setUsuarioId(usuarioId);
            em.persist(produto);
            if (stockNorm.compareTo(BigDecimal.ZERO) > 0) {
                MovimentoStock mov = new MovimentoStock(
                    usuarioId, produto, "STOCK_INICIAL", stockNorm, custoNorm,
                    "Stock inicial do produto", null, null, usuarioId
                );
                em.persist(mov);
            }
            return produto;
        });
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return actualizarProduto(id, nome, preco, null, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return actualizarProduto(id, nome, preco, custo, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        if (stock != null && quantidade(stock).compareTo(produto.getQuantidadeStock()) != 0) {
            throw new IllegalArgumentException("Não é permitido alterar o stock directamente. Use Entrada ou Ajuste de stock.");
        }
        validarProduto(nome, preco, produto.getQuantidadeStock());
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, id)) {
            throw new ConflitoException("Já existe um produto com este código de barras.");
        }
        produto.setNome(nome.trim());
        produto.setPreco(moeda(preco));
        if (custo != null) {
            produto.setCusto(moeda(custo));
        }
        produto.setStockMinimo(stockMinimo(stockMinimo));
        produto.setStockMaximo(stockMaximo != null ? quantidade(stockMaximo) : null);
        produto.setUnidade(unidade);
        produto.setCategoria(categoria);
        produto.setCodigoBarras(codigoNormalizado);
        return produtoDAO.actualizar(produto);
    }

    public void eliminarProduto(Long id, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        boolean temVendas = itemVendaDAO.existeParaProduto(id, usuarioId);
        boolean temMovimentos = new MovimentoStockDAO().temMovimentosParaProduto(usuarioId, id);
        if (temVendas || temMovimentos) {
            produto.setAtivo(false);
            produtoDAO.actualizar(produto);
        } else {
            produtoDAO.eliminar(id);
        }
    }

    public Produto buscarPorCodigoBarras(String codigo, Long usuarioId) {
        return produtoDAO.buscarPorCodigoBarras(codigo, usuarioId);
    }

    public List<Produto> listarProdutos(Long usuarioId) {
        return listarProdutos(usuarioId, false);
    }

    public List<Produto> listarProdutos(Long usuarioId, boolean incluirArquivados) {
        return produtoDAO.listarOrdenado(usuarioId, incluirArquivados);
    }

    public List<Produto> buscarProdutosPorNome(String nome, Long usuarioId) {
        return produtoDAO.buscarPorNome(nome, usuarioId);
    }

    public List<Produto> produtosComStockBaixo(Long usuarioId) {
        return produtoDAO.buscarStockBaixo(usuarioId);
    }

    public long totalProdutos(Long usuarioId) {
        return produtoDAO.contarTodos(usuarioId);
    }

    public Venda registarVenda(Long produtoId, BigDecimal quantidade, String observacao, Long usuarioId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("produtoId", produtoId);
        item.put("quantidade", quantidade);
        Map<String, Object> resultado = registarVendaLote(List.of(item), usuarioId);
        return (Venda) resultado.get("venda");
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens, Long usuarioId) {
        return registarVendaLote(itens, null, null, null, null, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        return registarVendaLote(itens, pagamentos, clienteId, observacao, null, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        try {
            return JPAUtil.emTransacao(usuarioId, em -> executarVendaLote(em, itens, pagamentos, clienteId, observacao, uuidCliente, usuarioId));
        } catch (PessimisticLockException e) {
            throw new ConflitoException("O stock está a ser actualizado por outra venda. Tente novamente.");
        }
    }

    public static Map<Long, BigDecimal> normalizarCarrinho(List<Map<String, Object>> itens) {
        if (itens == null || itens.isEmpty()) throw new IllegalArgumentException("Carrinho está vazio.");
        Map<Long, BigDecimal> quantidades = new TreeMap<>();
        for (Map<String, Object> item : itens) {
            if (item == null) throw new IllegalArgumentException("Item de venda inválido.");
            Long produtoId = longValue(item.get("produtoId"), "produtoId");
            BigDecimal qtd = quantidade(decimal(item.get("quantidade"), "quantidade"));
            if (produtoId <= 0 || qtd.signum() <= 0) throw new IllegalArgumentException("Produto e quantidade devem ser positivos.");
            quantidades.merge(produtoId, qtd, BigDecimal::add);
        }
        return quantidades;
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                Long usuarioId) {
        return executarVendaLote(em, quantidades, null, null, null, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        List<Map<String, Object>> itens = new ArrayList<>();
        if (quantidades != null) {
            for (Map.Entry<Long, BigDecimal> entry : quantidades.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("produtoId", entry.getKey());
                item.put("quantidade", entry.getValue());
                itens.add(item);
            }
        }
        return executarVendaLote(em, itens, pagamentosRaw, clienteId, observacao, null, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                List<Map<String, Object>> itensRaw,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        // 1. Idempotência por identificador gerado no cliente (uuid_cliente)
        if (uuidCliente != null && !uuidCliente.trim().isEmpty()) {
            Venda existente = vendaDAO.buscarPorUuidCliente(em, uuidCliente.trim(), usuarioId);
            if (existente != null) {
                Map<String, Object> resultado = new LinkedHashMap<>();
                resultado.put("id", existente.getId());
                resultado.put("numero", existente.getNumero());
                resultado.put("uuidCliente", existente.getUuidCliente());
                resultado.put("itens", existente.getItens() != null ? existente.getItens().size() : 0);
                resultado.put("total", moeda(existente.getTotal()));
                resultado.put("totalCusto", moeda(existente.getTotalCusto()));
                BigDecimal trocoExistente = BigDecimal.ZERO;
                if (existente.getPagamentos() != null) {
                    for (PagamentoVenda pv : existente.getPagamentos()) {
                        if (pv.getTroco() != null && pv.getTroco().compareTo(BigDecimal.ZERO) > 0) {
                            trocoExistente = pv.getTroco();
                            break;
                        }
                    }
                }
                resultado.put("troco", moeda(trocoExistente));
                resultado.put("estado", existente.getEstado());
                resultado.put("venda", existente);

                List<Map<String, Object>> itensLegados = new ArrayList<>();
                if (existente.getItens() != null) {
                    for (ItemVenda iv : existente.getItens()) {
                        itensLegados.add(util.VendaJson.itemVenda(iv));
                    }
                }
                resultado.put("vendas", itensLegados);
                return resultado;
            }
        }

        if (itensRaw == null || itensRaw.isEmpty()) {
            throw new IllegalArgumentException("Carrinho está vazio.");
        }

        // 2. Agrupar quantidades para validação e bloqueio pessimista de stock
        Map<Long, BigDecimal> quantidadesPorProduto = new TreeMap<>();
        for (Map<String, Object> item : itensRaw) {
            if (item == null) throw new IllegalArgumentException("Item de venda inválido.");
            Long produtoId = longValue(item.get("produtoId"), "produtoId");
            BigDecimal qtd = quantidade(decimal(item.get("quantidade"), "quantidade"));
            if (produtoId <= 0 || qtd.signum() <= 0) {
                throw new IllegalArgumentException("Produto e quantidade devem ser positivos.");
            }
            quantidadesPorProduto.merge(produtoId, qtd, BigDecimal::add);
        }

        Map<Long, Produto> produtos = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : quantidadesPorProduto.entrySet()) {
            Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, entry.getKey(), usuarioId);
            if (produto == null) throw new RecursoNaoEncontradoException("Produto não encontrado.");
            if (!produto.isAtivo()) {
                throw new ConflitoException("O produto " + produto.getNome() + " está arquivado e não pode ser vendido.");
            }
            if (produto.getQuantidadeStock().compareTo(entry.getValue()) < 0) {
                throw new ConflitoException("Stock insuficiente para " + produto.getNome() + ".");
            }
            produtos.put(entry.getKey(), produto);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalCusto = BigDecimal.ZERO;

        class LinhaCalculada {
            Produto produto;
            BigDecimal quantidade;
            BigDecimal precoUnitario;
            BigDecimal descontoPercentual;
            BigDecimal descontoValor;
            BigDecimal precoFinal;
            BigDecimal subtotal;
            BigDecimal subtotalCusto;
            String nota;
        }

        List<LinhaCalculada> linhas = new ArrayList<>();
        for (Map<String, Object> item : itensRaw) {
            Long produtoId = longValue(item.get("produtoId"), "produtoId");
            BigDecimal qtd = quantidade(decimal(item.get("quantidade"), "quantidade"));
            Produto produto = produtos.get(produtoId);
            BigDecimal precoUnit = produto.getPreco();

            BigDecimal descPerc = BigDecimal.ZERO;
            BigDecimal descVal = BigDecimal.ZERO;
            if (item.get("descontoPercentual") != null) {
                descPerc = moeda(decimal(item.get("descontoPercentual"), "descontoPercentual"));
            }
            if (item.get("descontoValor") != null) {
                descVal = moeda(decimal(item.get("descontoValor"), "descontoValor"));
            }

            BigDecimal precoFinal = precoUnit;
            if (descPerc.compareTo(BigDecimal.ZERO) > 0) {
                descVal = precoUnit.multiply(descPerc).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                precoFinal = precoUnit.subtract(descVal);
            } else if (descVal.compareTo(BigDecimal.ZERO) > 0) {
                precoFinal = precoUnit.subtract(descVal);
                if (precoUnit.compareTo(BigDecimal.ZERO) > 0) {
                    descPerc = descVal.multiply(BigDecimal.valueOf(100)).divide(precoUnit, 2, RoundingMode.HALF_UP);
                }
            } else if (item.get("precoFinal") != null) {
                precoFinal = moeda(decimal(item.get("precoFinal"), "precoFinal"));
                if (precoUnit.compareTo(precoFinal) > 0) {
                    descVal = precoUnit.subtract(precoFinal);
                    if (precoUnit.compareTo(BigDecimal.ZERO) > 0) {
                        descPerc = descVal.multiply(BigDecimal.valueOf(100)).divide(precoUnit, 2, RoundingMode.HALF_UP);
                    }
                }
            }

            if (precoFinal.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("O preço final após desconto não pode ser inferior a zero.");
            }

            BigDecimal subtotal = qtd.multiply(precoFinal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal custoUnit = produto.getCusto() != null ? produto.getCusto() : BigDecimal.ZERO;
            BigDecimal subtotalCusto = qtd.multiply(custoUnit).setScale(2, RoundingMode.HALF_UP);

            total = total.add(subtotal);
            totalCusto = totalCusto.add(subtotalCusto);

            LinhaCalculada lc = new LinhaCalculada();
            lc.produto = produto;
            lc.quantidade = qtd;
            lc.precoUnitario = precoUnit;
            lc.descontoPercentual = descPerc;
            lc.descontoValor = descVal;
            lc.precoFinal = precoFinal;
            lc.subtotal = subtotal;
            lc.subtotalCusto = subtotalCusto;
            lc.nota = item.get("nota") != null ? item.get("nota").toString().trim() : null;
            linhas.add(lc);
        }

        // 3. Validação de pagamentos e métodos configuráveis
        metodoPagamentoDAO.garantirMetodosPadrao(em, usuarioId);

        class PagamentoCalc {
            MetodoPagamento metodoPagamento;
            String metodoNome;
            String metodoTipo;
            BigDecimal valor;
            String referencia;
        }

        List<PagamentoCalc> pagamentosProcessados = new ArrayList<>();
        boolean temFiado = false;
        BigDecimal totalFiado = BigDecimal.ZERO;
        BigDecimal somaPagamentos = BigDecimal.ZERO;

        if (pagamentosRaw == null || pagamentosRaw.isEmpty()) {
            List<MetodoPagamento> ativos = metodoPagamentoDAO.listarAtivos(em, usuarioId);
            MetodoPagamento mpDinheiro = ativos.stream()
                    .filter(m -> "DINHEIRO".equalsIgnoreCase(m.getTipo()) || "Dinheiro".equalsIgnoreCase(m.getNome()))
                    .findFirst().orElse(null);
            PagamentoCalc pc = new PagamentoCalc();
            pc.metodoPagamento = mpDinheiro;
            pc.metodoNome = mpDinheiro != null ? mpDinheiro.getNome() : "Dinheiro";
            pc.metodoTipo = "DINHEIRO";
            pc.valor = total;
            pagamentosProcessados.add(pc);
            somaPagamentos = total;
        } else {
            class PrePagamento {
                Map<String, Object> raw;
                MetodoPagamento mp;
                String metodoNome;
                String metodoTipo;
                boolean isFiado;
            }
            List<PrePagamento> preList = new ArrayList<>();
            BigDecimal somaOutros = BigDecimal.ZERO;
            PrePagamento fiadoSemValor = null;

            for (Map<String, Object> p : pagamentosRaw) {
                if (p == null) continue;
                MetodoPagamento mp = null;
                if (p.get("metodoId") != null) {
                    Long mid = longValue(p.get("metodoId"), "metodoId");
                    mp = metodoPagamentoDAO.buscarPorId(em, mid, usuarioId);
                    if (mp == null) {
                        throw new RecursoNaoEncontradoException("Método de pagamento não encontrado.");
                    }
                } else if (p.get("metodo") != null) {
                    String mStr = p.get("metodo").toString().trim();
                    mp = metodoPagamentoDAO.buscarPorNome(em, mStr, usuarioId);
                    if (mp == null) {
                        String mUpper = mStr.toUpperCase();
                        String fallbackNome = null;
                        switch (mUpper) {
                            case "MPESA": fallbackNome = "M-Pesa"; break;
                            case "EMOLA": fallbackNome = "e-Mola"; break;
                            case "MKESH": fallbackNome = "mKesh"; break;
                            case "CARTAO": fallbackNome = "Cartão"; break;
                            case "FIADO": fallbackNome = "A fiado"; break;
                            case "DINHEIRO": fallbackNome = "Dinheiro"; break;
                            default: fallbackNome = null; break;
                        }
                        if (fallbackNome != null) {
                            mp = metodoPagamentoDAO.buscarPorNome(em, fallbackNome, usuarioId);
                        }
                    }
                    if (mp == null) {
                        throw new IllegalArgumentException("Método de pagamento inválido ou não encontrado: " + mStr);
                    }
                } else {
                    throw new IllegalArgumentException("Método de pagamento não informado.");
                }

                if (!Boolean.TRUE.equals(mp.getAtivo())) {
                    throw new IllegalArgumentException("O método de pagamento " + mp.getNome() + " está inactivo e não pode receber novas vendas.");
                }

                String metodoNome = mp.getNome();
                String metodoTipo = mp.getTipo();
                boolean isFiado = "FIADO".equalsIgnoreCase(metodoTipo) || "A fiado".equalsIgnoreCase(metodoNome);

                PrePagamento prep = new PrePagamento();
                prep.raw = p;
                prep.mp = mp;
                prep.metodoNome = metodoNome;
                prep.metodoTipo = metodoTipo;
                prep.isFiado = isFiado;
                preList.add(prep);

                if (isFiado) {
                    temFiado = true;
                    Object vObj = p.get("valor");
                    if (vObj == null || vObj.toString().trim().isEmpty()) {
                        fiadoSemValor = prep;
                    } else {
                        BigDecimal v = moeda(decimal(vObj, "valor"));
                        if (v.compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
                        }
                    }
                } else {
                    Object vObj = p.get("valor");
                    if (vObj == null || vObj.toString().trim().isEmpty()) {
                        throw new IllegalArgumentException("O valor do pagamento em " + metodoNome + " é obrigatório.");
                    }
                    BigDecimal v = moeda(decimal(vObj, "valor"));
                    if (v.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
                    }
                    somaOutros = somaOutros.add(v);
                }
            }

            for (PrePagamento prep : preList) {
                BigDecimal valor;
                if (prep.isFiado && prep == fiadoSemValor) {
                    valor = total.subtract(somaOutros);
                    if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("O valor a fiado calculado deve ser maior que zero.");
                    }
                } else {
                    valor = moeda(decimal(prep.raw.get("valor"), "valor"));
                }

                if (prep.isFiado) {
                    totalFiado = totalFiado.add(valor);
                }
                somaPagamentos = somaPagamentos.add(valor);

                PagamentoCalc pc = new PagamentoCalc();
                pc.metodoPagamento = prep.mp;
                pc.metodoNome = prep.metodoNome;
                pc.metodoTipo = prep.metodoTipo;
                pc.valor = valor;
                pc.referencia = prep.raw.get("referencia") != null ? prep.raw.get("referencia").toString().trim() : null;
                pagamentosProcessados.add(pc);
            }
        }

        if (temFiado) {
            if (clienteId == null) {
                throw new IllegalArgumentException("Para vendas a fiado é obrigatório associar um cliente / devedor.");
            }
            Devedor cliente = devedorDAO.buscarPorIdParaBloqueio(em, clienteId, usuarioId);
            if (cliente == null) {
                throw new RecursoNaoEncontradoException("Cliente não encontrado.");
            }
            if (somaPagamentos.compareTo(total) != 0) {
                throw new IllegalArgumentException("Com fiado, a soma dos pagamentos tem de ser igual ao total.");
            }
        } else {
            if (somaPagamentos.compareTo(total) < 0) {
                throw new IllegalArgumentException("O valor total dos pagamentos é inferior ao total da venda.");
            }
        }

        BigDecimal troco = BigDecimal.ZERO;
        if (!temFiado && somaPagamentos.compareTo(total) > 0) {
            boolean temDinheiro = pagamentosProcessados.stream().anyMatch(p -> "DINHEIRO".equalsIgnoreCase(p.metodoTipo));
            if (!temDinheiro) {
                throw new IllegalArgumentException("Troco só é permitido para pagamentos em dinheiro.");
            }
            troco = somaPagamentos.subtract(total);
        }

        // 4. Criação e persistência do cabeçalho da venda
        Long numero = vendaDAO.proximoNumero(em, usuarioId);
        Venda venda = new Venda(usuarioId, numero, LocalDateTime.now(), total, totalCusto, "CONCLUIDA", clienteId, observacao, usuarioId);
        if (uuidCliente != null && !uuidCliente.trim().isEmpty()) {
            venda.setUuidCliente(uuidCliente.trim());
        }
        SessaoCaixa sessaoAberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
        if (sessaoAberta != null) {
            venda.setSessaoCaixa(sessaoAberta);
        }
        em.persist(venda);
        em.flush();

        if (temFiado && clienteId != null) {
            Devedor cliente = devedorDAO.buscarPorIdParaBloqueio(em, clienteId, usuarioId);
            if (cliente != null) {
                cliente.setDivida(cliente.getDivida().add(totalFiado));
                if (cliente.getVendaId() == null) {
                    cliente.setVendaId(venda.getId());
                }
            }
        }

        // 5. Criação das linhas da venda
        List<ItemVenda> itensCriados = new ArrayList<>();
        StockService stockService = new StockService();
        for (LinhaCalculada lc : linhas) {
            ItemVenda iv = new ItemVenda(venda, lc.produto, lc.quantidade, lc.precoUnitario,
                lc.descontoPercentual, lc.descontoValor, lc.precoFinal,
                lc.produto.getCusto() != null ? lc.produto.getCusto() : BigDecimal.ZERO,
                observacao, lc.nota, usuarioId);
            em.persist(iv);
            itensCriados.add(iv);
            venda.addItem(iv);
        }

        // 6. Desconto de stock
        for (Map.Entry<Long, BigDecimal> entry : quantidadesPorProduto.entrySet()) {
            Produto produto = produtos.get(entry.getKey());
            stockService.registarMovimento(em, usuarioId, produto.getId(), "VENDA",
                entry.getValue().negate(), produto.getCusto(), "Venda #" + numero,
                "VENDA", venda.getId(), usuarioId);
        }

        // 7. Registo dos pagamentos
        BigDecimal trocoRestante = troco;
        for (PagamentoCalc pc : pagamentosProcessados) {
            BigDecimal trocoDoItem = BigDecimal.ZERO;
            if ("DINHEIRO".equalsIgnoreCase(pc.metodoTipo) && trocoRestante.compareTo(BigDecimal.ZERO) > 0) {
                trocoDoItem = trocoRestante;
                trocoRestante = BigDecimal.ZERO;
            }
            String metodoCodigo = "DINHEIRO";
            if ("Dinheiro".equalsIgnoreCase(pc.metodoNome) || "DINHEIRO".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "DINHEIRO";
            } else if ("M-Pesa".equalsIgnoreCase(pc.metodoNome) || "MPESA".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "MPESA";
            } else if ("e-Mola".equalsIgnoreCase(pc.metodoNome) || "EMOLA".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "EMOLA";
            } else if ("mKesh".equalsIgnoreCase(pc.metodoNome) || "MKESH".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "MKESH";
            } else if ("Cartão".equalsIgnoreCase(pc.metodoNome) || "Cartao".equalsIgnoreCase(pc.metodoNome) || "CARTAO".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "CARTAO";
            } else if ("A fiado".equalsIgnoreCase(pc.metodoNome) || "FIADO".equalsIgnoreCase(pc.metodoNome)) {
                metodoCodigo = "FIADO";
            } else {
                metodoCodigo = pc.metodoTipo != null ? pc.metodoTipo.toUpperCase() : "DIGITAL";
            }
            PagamentoVenda pv = new PagamentoVenda(usuarioId, venda, metodoCodigo, pc.metodoPagamento, pc.referencia, pc.valor, trocoDoItem);
            em.persist(pv);
            venda.addPagamento(pv);
        }
        em.flush();

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", venda.getId());
        resultado.put("numero", venda.getNumero());
        resultado.put("uuidCliente", venda.getUuidCliente());
        resultado.put("itens", itensCriados.size());
        resultado.put("total", moeda(total));
        resultado.put("totalCusto", moeda(totalCusto));
        resultado.put("troco", moeda(troco));
        resultado.put("estado", venda.getEstado());
        resultado.put("venda", venda);

        List<Map<String, Object>> itensLegados = new ArrayList<>();
        for (ItemVenda iv : itensCriados) {
            itensLegados.add(util.VendaJson.itemVenda(iv));
        }
        resultado.put("vendas", itensLegados);

        return resultado;
    }

    public Venda anularVenda(Long vendaId, String motivo, Long usuarioId) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("O motivo da anulação é obrigatório.");
        }
        return JPAUtil.emTransacao(usuarioId, em -> {
            Venda venda = em.find(Venda.class, vendaId, LockModeType.PESSIMISTIC_WRITE);
            if (venda == null || !usuarioId.equals(venda.getUsuarioId())) {
                throw new RecursoNaoEncontradoException("Venda não encontrada.");
            }
            if ("ANULADA".equals(venda.getEstado())) {
                return venda; // Idempotente
            }
            if ("PARCIALMENTE_DEVOLVIDA".equals(venda.getEstado()) || "DEVOLVIDA".equals(venda.getEstado())) {
                throw new ConflitoException("Não é possível anular uma venda com devoluções registadas.");
            }
            if (!"CONCLUIDA".equals(venda.getEstado())) {
                throw new IllegalStateException("Esta venda não pode ser anulada (estado actual: " + venda.getEstado() + ").");
            }

            List<PagamentoVenda> pagamentos = em.createQuery(
                "SELECT p FROM PagamentoVenda p WHERE p.venda.id = :vid AND p.usuarioId = :uid", PagamentoVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            PagamentoVenda fiado = pagamentos.stream()
                .filter(p -> "FIADO".equalsIgnoreCase(p.getMetodo()))
                .findFirst()
                .orElse(null);

            if (fiado != null && venda.getClienteId() != null) {
                Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, venda.getClienteId(), usuarioId);
                if (devedor != null) {
                    boolean temPagamentos = pagamentoDividaDAO.temPagamentos(usuarioId, devedor.getId());
                    if (temPagamentos) {
                        throw new ConflitoException("Não é possível anular a venda porque o cliente já efectuou pagamentos desta dívida.");
                    }
                    BigDecimal novaDivida = devedor.getDivida().subtract(fiado.getValor());
                    if (novaDivida.compareTo(BigDecimal.ZERO) < 0) novaDivida = BigDecimal.ZERO;
                    devedor.setDivida(novaDivida);
                }
            }

            venda.setEstado("ANULADA");
            venda.setAnuladaEm(LocalDateTime.now());
            venda.setAnuladaPor(usuarioId);
            venda.setMotivoAnulacao(motivo.trim());

            List<ItemVenda> itens = em.createQuery(
                "SELECT i FROM ItemVenda i WHERE i.venda.id = :vid AND i.usuarioId = :uid", ItemVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            StockService stockService = new StockService();
            for (ItemVenda iv : itens) {
                stockService.registarMovimento(em, usuarioId, iv.getProduto().getId(), "DEVOLUCAO",
                    iv.getQuantidade(), iv.getCustoUnitario(),
                    "Anulação da venda #" + venda.getNumero() + ": " + motivo.trim(),
                    "VENDA", venda.getId(), usuarioId);
            }

            return venda;
        });
    }

    public Map<String, Object> fechoCaixa(LocalDate data, Long usuarioId) {
        LocalDate dia = data != null ? data : LocalDate.now();
        BigDecimal totalVendido = vendaDAO.totalVendasPeriodo(dia, dia, usuarioId);
        BigDecimal totalCusto = vendaDAO.totalCustoPeriodo(dia, dia, usuarioId);
        BigDecimal lucroEstimado = vendaDAO.lucroEstimadoPeriodo(dia, dia, usuarioId);
        long numeroVendas = vendaDAO.contarVendasDia(dia, usuarioId);
        long vendasAnuladas = vendaDAO.contarVendasAnuladasHoje(usuarioId, dia);
        Map<String, BigDecimal> totaisMetodo = pagamentoVendaDAO.totaisPorMetodo(usuarioId, dia);
        BigDecimal totalFiado = totaisMetodo.getOrDefault("FIADO", BigDecimal.ZERO);

        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime fimDia = dia.plusDays(1).atStartOfDay();
        BigDecimal totalDespesas = JPAUtil.emTransacao(usuarioId, em -> despesaCaixaDAO.totalDespesasPeriodo(em, inicioDia, fimDia, usuarioId));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("data", dia.toString());
        res.put("totalVendido", decimalJson(totalVendido, 2));
        res.put("totalCusto", decimalJson(totalCusto, 2));
        res.put("lucroEstimado", decimalJson(lucroEstimado, 2));
        res.put("numeroVendas", numeroVendas);
        res.put("vendasAnuladas", vendasAnuladas);
        res.put("totalFiado", decimalJson(totalFiado, 2));
        res.put("totalDespesas", decimalJson(totalDespesas, 2));
        Map<String, String> formatados = new LinkedHashMap<>();
        totaisMetodo.forEach((k, v) -> formatados.put(k, decimalJson(v, 2)));
        res.put("totaisPorMetodo", formatados);
        return res;
    }

    public Map<String, Object> registarPagamentoDivida(Long devedorId, BigDecimal valor, String metodo, String observacao, Long usuarioId) {
        if (metodo == null || metodo.trim().isEmpty()) {
            throw new IllegalArgumentException("O método de pagamento é obrigatório.");
        }
        String metNorm = metodo.trim().toUpperCase();
        if (!List.of("DINHEIRO", "MPESA", "EMOLA", "MKESH", "CARTAO").contains(metNorm)) {
            throw new IllegalArgumentException("Método de pagamento de dívida inválido: " + metNorm);
        }
        BigDecimal valorNorm = moeda(decimal(valor, "valor"));
        if (valorNorm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
        }

        return JPAUtil.emTransacao(usuarioId, em -> {
            Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, devedorId, usuarioId);
            if (devedor == null) {
                throw new RecursoNaoEncontradoException("Devedor não encontrado.");
            }
            BigDecimal totalPagoAnterior = pagamentoDividaDAO.totalPagoPorDevedor(usuarioId, devedorId);
            BigDecimal saldoActual = devedor.getDivida().subtract(totalPagoAnterior);
            if (valorNorm.compareTo(saldoActual) > 0) {
                throw new IllegalArgumentException("O valor a pagar (" + valorNorm + ") é superior ao saldo da dívida (" + saldoActual + ").");
            }

            PagamentoDivida pag = new PagamentoDivida(
                usuarioId, devedor, valorNorm, metNorm, LocalDateTime.now(), observacao);
            em.persist(pag);

            BigDecimal novoSaldo = saldoActual.subtract(valorNorm);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", pag.getId());
            res.put("devedorId", devedor.getId());
            res.put("devedorNome", devedor.getNome());
            res.put("valor", decimalJson(valorNorm, 2));
            res.put("metodo", metNorm);
            res.put("criadoEm", pag.getCriadoEm().toString());
            res.put("observacao", observacao != null ? observacao : "");
            res.put("saldoRestante", decimalJson(novoSaldo, 2));
            return res;
        });
    }

    public List<Venda> listarVendas(Long usuarioId) {
        return vendaDAO.listarOrdenado(usuarioId);
    }

    public Venda buscarVendaPorId(Long id, Long usuarioId) {
        Venda v = vendaDAO.buscarPorIdComDetalhes(id, usuarioId);
        if (v == null) {
            throw new RecursoNaoEncontradoException("Venda não encontrada.");
        }
        return v;
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        return vendaDAO.recentes(usuarioId, 25);
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.listarPagina(usuarioId, 1, 100, "", inicio, fim, null, "CONCLUIDA").getItems();
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return vendaDAO.totalVendasHoje(usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.totalVendasPeriodo(inicio, fim, usuarioId);
    }

    public long vendasHoje(Long usuarioId) {
        return vendaDAO.contarVendasHoje(usuarioId);
    }

    public static BigDecimal decimal(Object value, String campo) {
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser numérico.");
        }
    }

    public List<model.MetodoPagamento> listarMetodosPagamento(Long usuarioId) {
        return metodoPagamentoDAO.listarTodos(usuarioId);
    }

    public List<model.MetodoPagamento> listarMetodosPagamentoAtivos(Long usuarioId) {
        return metodoPagamentoDAO.listarAtivos(usuarioId);
    }

    public model.MetodoPagamento criarMetodoPagamento(String nome, String tipo, Long usuarioId) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do método de pagamento é obrigatório.");
        }
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DIGITAL";
        if (!List.of("DINHEIRO", "DIGITAL", "FIADO").contains(tipoNorm)) {
            throw new IllegalArgumentException("Tipo de método de pagamento inválido: " + tipoNorm);
        }
        return JPAUtil.emTransacao(usuarioId, em -> {
            model.MetodoPagamento existente = metodoPagamentoDAO.buscarPorNome(em, nome.trim(), usuarioId);
            if (existente != null) {
                throw new ConflitoException("Já existe um método de pagamento com o nome " + nome.trim() + ".");
            }
            Long count = em.createQuery("SELECT COUNT(m) FROM MetodoPagamento m WHERE m.usuarioId = :uid", Long.class)
                    .setParameter("uid", usuarioId).getSingleResult();
            model.MetodoPagamento novo = new model.MetodoPagamento(usuarioId, nome.trim(), tipoNorm, true, count.intValue() + 1);
            em.persist(novo);
            return novo;
        });
    }

    public model.MetodoPagamento alternarMetodoPagamento(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            model.MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            metodo.setAtivo(!metodo.getAtivo());
            em.merge(metodo);
            return metodo;
        });
    }

    public model.MetodoPagamento actualizarMetodoPagamento(Long id, String nome, Boolean ativo, Integer ordem, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            model.MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            if (nome != null && !nome.trim().isEmpty()) {
                model.MetodoPagamento comMesmoNome = metodoPagamentoDAO.buscarPorNome(em, nome.trim(), usuarioId);
                if (comMesmoNome != null && !comMesmoNome.getId().equals(id)) {
                    throw new ConflitoException("Já existe um método de pagamento com o nome " + nome.trim() + ".");
                }
                metodo.setNome(nome.trim());
            }
            if (ativo != null) metodo.setAtivo(ativo);
            if (ordem != null) metodo.setOrdem(ordem);
            em.merge(metodo);
            return metodo;
        });
    }

    public void eliminarMetodoPagamento(Long id, Long usuarioId) {
        JPAUtil.emTransacao(usuarioId, em -> {
            model.MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            Long vendasComMetodo = em.createQuery(
                "SELECT COUNT(pv) FROM PagamentoVenda pv WHERE pv.metodoPagamento.id = :mid AND pv.usuarioId = :uid", Long.class)
                .setParameter("mid", id)
                .setParameter("uid", usuarioId)
                .getSingleResult();
            if (vendasComMetodo > 0) {
                throw new IllegalStateException("Não é possível apagar um método de pagamento com vendas associadas. Desactive-o em vez de apagar.");
            }
            em.remove(metodo);
            return null;
        });
    }

    public DefinicaoLoja obterDefinicoesLoja(Long usuarioId) {
        return definicaoLojaDAO.obter(usuarioId);
    }

    public DefinicaoLoja atualizarDefinicoesLoja(boolean controloCaixa, String nomeLoja, Long usuarioId) {
        return definicaoLojaDAO.atualizar(usuarioId, controloCaixa, nomeLoja);
    }

    public SessaoCaixa abrirSessaoCaixa(BigDecimal valorInicial, String notaAbertura, Long operadorId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            if (aberta != null) {
                throw new ConflitoException("Já existe uma sessão de caixa aberta.");
            }
            BigDecimal inicial = valorInicial != null ? moeda(valorInicial) : BigDecimal.ZERO;
            if (inicial.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("O valor inicial não pode ser negativo.");
            }
            SessaoCaixa sessao = new SessaoCaixa(usuarioId, inicial, notaAbertura != null ? notaAbertura.trim() : null, operadorId);
            em.persist(sessao);
            return sessao;
        });
    }

    public SessaoCaixa buscarSessaoAberta(Long usuarioId) {
        return sessaoCaixaDAO.buscarAberta(usuarioId);
    }

    public Map<String, Object> obterResumoSessaoAtual(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            Map<String, Object> res = new LinkedHashMap<>();
            if (aberta == null) {
                res.put("aberta", false);
                return res;
            }
            BigDecimal vendasDinheiro = sessaoCaixaDAO.calcularVendasDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal devolucoesDinheiro = sessaoCaixaDAO.calcularDevolucoesDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal despesasDinheiro = sessaoCaixaDAO.calcularDespesasDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal esperado = aberta.getValorInicial().add(vendasDinheiro).subtract(devolucoesDinheiro).subtract(despesasDinheiro);

            Long totalVendas = em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.sessaoCaixa.id = :sid AND v.usuarioId = :uid AND v.estado != 'ANULADA'", Long.class)
                .setParameter("sid", aberta.getId())
                .setParameter("uid", usuarioId)
                .getSingleResult();

            res.put("aberta", true);
            res.put("id", aberta.getId());
            res.put("abertaEm", aberta.getAbertaEm().toString());
            res.put("valorInicial", decimalJson(aberta.getValorInicial(), 2));
            res.put("vendasDinheiro", decimalJson(vendasDinheiro, 2));
            res.put("devolucoesDinheiro", decimalJson(devolucoesDinheiro, 2));
            res.put("despesasDinheiro", decimalJson(despesasDinheiro, 2));
            res.put("valorEsperado", decimalJson(esperado, 2));
            res.put("totalVendas", totalVendas);
            res.put("notaAbertura", aberta.getNotaAbertura() != null ? aberta.getNotaAbertura() : "");
            return res;
        });
    }

    public SessaoCaixa fecharSessaoCaixa(Long sessaoId, BigDecimal valorContado, String notaFecho, Long operadorId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa sessao = sessaoCaixaDAO.buscarPorId(em, sessaoId, usuarioId);
            if (sessao == null) {
                throw new RecursoNaoEncontradoException("Sessão de caixa não encontrada.");
            }
            if (!"ABERTA".equals(sessao.getEstado())) {
                throw new IllegalStateException("Esta sessão de caixa já está fechada.");
            }

            BigDecimal vendasDinheiro = sessaoCaixaDAO.calcularVendasDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal devolucoesDinheiro = sessaoCaixaDAO.calcularDevolucoesDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal despesasDinheiro = sessaoCaixaDAO.calcularDespesasDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal esperado = sessao.getValorInicial().add(vendasDinheiro).subtract(devolucoesDinheiro).subtract(despesasDinheiro);

            BigDecimal contado = valorContado != null ? moeda(valorContado) : BigDecimal.ZERO;
            BigDecimal diferenca = contado.subtract(esperado);

            sessao.setValorContado(contado);
            sessao.setValorEsperado(esperado);
            sessao.setDiferenca(diferenca);
            sessao.setNotaFecho(notaFecho != null ? notaFecho.trim() : null);
            sessao.setFechadaEm(LocalDateTime.now());
            sessao.setFechadaPor(operadorId);
            sessao.setEstado("FECHADA");

            return em.merge(sessao);
        });
    }

    public Map<String, Object> registarDespesaCaixa(BigDecimal valor, String categoria, String descricao, Long operadorId, Long usuarioId) {
        if (descricao == null || descricao.trim().isEmpty()) {
            throw new IllegalArgumentException("A descrição da despesa é obrigatória.");
        }
        BigDecimal valorNorm = moeda(decimal(valor, "valor"));
        if (valorNorm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da despesa deve ser maior que zero.");
        }

        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            if (aberta == null) {
                DefinicaoLoja def = definicaoLojaDAO.obter(usuarioId);
                if (def.getControloCaixa()) {
                    throw new IllegalStateException("Não há nenhuma sessão de caixa aberta para registar a saída.");
                }
            }

            DespesaCaixa despesa = new DespesaCaixa(usuarioId, aberta, valorNorm, categoria, descricao.trim(), operadorId);
            em.persist(despesa);
            em.flush();

            return formatarDespesaCaixa(despesa);
        });
    }

    public List<Map<String, Object>> listarDespesasCaixa(Long sessaoId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<DespesaCaixa> lista;
            if (sessaoId != null) {
                lista = despesaCaixaDAO.listarPorSessao(em, sessaoId, usuarioId);
            } else {
                SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
                if (aberta != null) {
                    lista = despesaCaixaDAO.listarPorSessao(em, aberta.getId(), usuarioId);
                } else {
                    lista = despesaCaixaDAO.listarRecentes(em, usuarioId, 50);
                }
            }
            List<Map<String, Object>> res = new ArrayList<>();
            for (DespesaCaixa d : lista) {
                res.add(formatarDespesaCaixa(d));
            }
            return res;
        });
    }

    private Map<String, Object> formatarDespesaCaixa(DespesaCaixa d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("sessaoId", d.getSessaoCaixa() != null ? d.getSessaoCaixa().getId() : null);
        m.put("valor", decimalJson(d.getValor(), 2));
        m.put("categoria", d.getCategoria());
        m.put("descricao", d.getDescricao());
        m.put("criadaEm", d.getCriadaEm() != null ? d.getCriadaEm().toString() : "");
        m.put("criadoPor", d.getCriadoPor());
        return m;
    }

    public List<SessaoCaixa> listarHistoricoSessoes(Long usuarioId, int limite) {
        return sessaoCaixaDAO.listarHistorico(usuarioId, limite);
    }

    public static class DevolucaoItemParam {
        public Long itemVendaId;
        public BigDecimal quantidade;

        public DevolucaoItemParam() {}
        public DevolucaoItemParam(Long itemVendaId, BigDecimal quantidade) {
            this.itemVendaId = itemVendaId;
            this.quantidade = quantidade;
        }
    }

    public Map<String, Object> processarDevolucao(Long vendaId, List<DevolucaoItemParam> itensParam, String motivo, String uuidCliente, Long operadorId, Long usuarioId) {
        if (itensParam == null || itensParam.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para devolução.");
        }
        String motivoNorm = (motivo != null && !motivo.trim().isEmpty()) ? motivo.trim() : "Devolução de produtos";

        return JPAUtil.emTransacao(usuarioId, em -> {
            if (uuidCliente != null && !uuidCliente.trim().isEmpty()) {
                Devolucao existente = devolucaoDAO.buscarPorUuidCliente(em, uuidCliente.trim(), usuarioId);
                if (existente != null) {
                    return formatarDevolucao(existente);
                }
            }

            Venda venda = em.find(Venda.class, vendaId, LockModeType.PESSIMISTIC_WRITE);
            if (venda == null || !usuarioId.equals(venda.getUsuarioId())) {
                throw new RecursoNaoEncontradoException("Venda não encontrada.");
            }
            if ("ANULADA".equals(venda.getEstado())) {
                throw new IllegalStateException("Esta venda está anulada e não pode receber devoluções.");
            }

            List<ItemVenda> itensVenda = em.createQuery(
                "SELECT iv FROM ItemVenda iv WHERE iv.venda.id = :vid AND iv.usuarioId = :uid", ItemVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            Map<Long, ItemVenda> itensMap = new HashMap<>();
            BigDecimal totalQtdVendida = BigDecimal.ZERO;
            for (ItemVenda iv : itensVenda) {
                itensMap.put(iv.getId(), iv);
                totalQtdVendida = totalQtdVendida.add(iv.getQuantidade());
            }

            BigDecimal totalQtdAnteriorDevolvida = BigDecimal.ZERO;
            for (ItemVenda iv : itensVenda) {
                BigDecimal devAnterior = devolucaoDAO.obterTotalDevolvidoPorItem(em, iv.getId());
                totalQtdAnteriorDevolvida = totalQtdAnteriorDevolvida.add(devAnterior);
            }

            BigDecimal totalDevolver = BigDecimal.ZERO;
            BigDecimal totalQtdDevolvidaAgora = BigDecimal.ZERO;
            List<ItemDevolucao> itensDevolucao = new ArrayList<>();
            StockService stockService = new StockService();

            for (DevolucaoItemParam param : itensParam) {
                if (param == null || param.itemVendaId == null) continue;
                ItemVenda iv = itensMap.get(param.itemVendaId);
                if (iv == null) {
                    throw new RecursoNaoEncontradoException("Item de venda " + param.itemVendaId + " não encontrado na venda #" + venda.getNumero() + ".");
                }
                BigDecimal qtdDevolver = quantidade(param.quantidade);
                if (qtdDevolver.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("A quantidade a devolver deve ser maior que zero.");
                }
                BigDecimal jaDevolvido = devolucaoDAO.obterTotalDevolvidoPorItem(em, iv.getId());
                BigDecimal disponivel = iv.getQuantidade().subtract(jaDevolvido);
                if (qtdDevolver.compareTo(disponivel) > 0) {
                    throw new IllegalArgumentException("Não é possível devolver mais do que o vendido. Disponível: " + disponivel.toPlainString());
                }

                BigDecimal precoUnit = iv.getPrecoFinal() != null ? iv.getPrecoFinal() : iv.getPrecoUnitario();
                BigDecimal valorItem = qtdDevolver.multiply(precoUnit).setScale(2, RoundingMode.HALF_UP);
                totalDevolver = totalDevolver.add(valorItem);
                totalQtdDevolvidaAgora = totalQtdDevolvidaAgora.add(qtdDevolver);

                stockService.registarMovimento(em, usuarioId, iv.getProduto().getId(), "DEVOLUCAO",
                    qtdDevolver, iv.getCustoUnitario(),
                    "Devolução da venda #" + venda.getNumero() + ": " + motivoNorm,
                    "DEVOLUCAO", venda.getId(), operadorId);

                ItemDevolucao idv = new ItemDevolucao(null, iv, qtdDevolver, valorItem);
                itensDevolucao.add(idv);
            }

            if (itensDevolucao.isEmpty()) {
                throw new IllegalArgumentException("Nenhum item válido para devolução.");
            }

            PagamentoVenda fiado = em.createQuery(
                "SELECT p FROM PagamentoVenda p WHERE p.venda.id = :vid AND p.usuarioId = :uid AND UPPER(p.metodo) = 'FIADO'", PagamentoVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultStream().findFirst().orElse(null);

            if (fiado != null && venda.getClienteId() != null) {
                Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, venda.getClienteId(), usuarioId);
                if (devedor != null) {
                    BigDecimal abatimento = totalDevolver.min(devedor.getDivida());
                    devedor.setDivida(devedor.getDivida().subtract(abatimento));
                }
            }

            BigDecimal totalQtdFinalDevolvida = totalQtdAnteriorDevolvida.add(totalQtdDevolvidaAgora);
            if (totalQtdFinalDevolvida.compareTo(totalQtdVendida) >= 0) {
                venda.setEstado("DEVOLVIDA");
            } else {
                venda.setEstado("PARCIALMENTE_DEVOLVIDA");
            }
            em.merge(venda);

            Devolucao devolucao = new Devolucao(usuarioId, venda, totalDevolver, motivoNorm, operadorId, uuidCliente);
            em.persist(devolucao);
            for (ItemDevolucao itemDev : itensDevolucao) {
                itemDev.setDevolucao(devolucao);
                em.persist(itemDev);
                devolucao.addItem(itemDev);
            }
            em.flush();

            return formatarDevolucao(devolucao);
        });
    }

    public List<Map<String, Object>> listarDevolucoesVenda(Long vendaId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Venda venda = vendaDAO.buscarPorId(em, vendaId, usuarioId);
            if (venda == null) {
                throw new RecursoNaoEncontradoException("Venda não encontrada.");
            }
            List<Devolucao> devs = devolucaoDAO.listarPorVenda(em, vendaId, usuarioId);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Devolucao d : devs) {
                list.add(formatarDevolucao(d));
            }
            return list;
        });
    }

    private Map<String, Object> formatarDevolucao(Devolucao dev) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", dev.getId());
        res.put("vendaId", dev.getVenda().getId());
        res.put("vendaNumero", dev.getVenda().getNumero());
        res.put("total", decimalJson(dev.getTotal(), 2));
        res.put("motivo", dev.getMotivo());
        res.put("criadaEm", dev.getCriadaEm() != null ? dev.getCriadaEm().toString() : "");
        res.put("estadoVenda", dev.getVenda().getEstado());
        List<Map<String, Object>> itens = new ArrayList<>();
        if (dev.getItens() != null) {
            for (ItemDevolucao idv : dev.getItens()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", idv.getId());
                m.put("itemVendaId", idv.getItemVenda().getId());
                m.put("produtoNome", idv.getItemVenda().getProduto().getNome());
                m.put("quantidade", decimalJson(idv.getQuantidade(), 3));
                m.put("valor", decimalJson(idv.getValor(), 2));
                itens.add(m);
            }
        }
        res.put("itens", itens);
        return res;
    }

    public List<Map<String, Object>> obterProdutosFaltaRepor(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Produto> lista = em.createQuery(
                "SELECT p FROM Produto p WHERE p.usuarioId = :uid AND p.ativo = true AND p.quantidadeStock <= p.stockMinimo " +
                "ORDER BY (p.quantidadeStock - p.stockMinimo) ASC, p.nome ASC", Produto.class)
                .setParameter("uid", usuarioId)
                .getResultList();

            List<Map<String, Object>> res = new ArrayList<>();
            for (Produto p : lista) {
                BigDecimal atual = p.getQuantidadeStock();
                BigDecimal min = p.getStockMinimo() != null ? p.getStockMinimo() : BigDecimal.ZERO;
                BigDecimal max = p.getStockMaximo();
                BigDecimal sugerido;
                if (max != null && max.compareTo(atual) > 0) {
                    sugerido = max.subtract(atual);
                } else {
                    sugerido = min.multiply(new BigDecimal("2")).subtract(atual);
                }
                if (sugerido.compareTo(BigDecimal.ONE) < 0) {
                    sugerido = BigDecimal.ONE;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", p.getId());
                item.put("nome", p.getNome());
                item.put("stockAtual", decimalJson(atual, 3));
                item.put("stockMinimo", decimalJson(min, 3));
                item.put("stockMaximo", max != null ? decimalJson(max, 3) : null);
                item.put("quantidadeSugerida", decimalJson(sugerido, 3));
                item.put("unidade", p.getUnidade());
                item.put("preco", decimalJson(p.getPreco(), 2));
                item.put("custo", decimalJson(p.getCusto(), 2));
                res.add(item);
            }
            return res;
        });
    }

    public BigDecimal calcularPerdasMesACusto(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            try {
                LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                BigDecimal perdas = em.createQuery(
                    "SELECT SUM(ABS(m.quantidade) * m.custoUnitario) FROM MovimentoStock m " +
                    "WHERE m.usuarioId = :uid AND (m.tipo = 'PERDA' OR (m.tipo = 'AJUSTE' AND m.quantidade < 0)) " +
                    "AND m.criadoEm >= :inicioMes", BigDecimal.class)
                    .setParameter("uid", usuarioId)
                    .setParameter("inicioMes", inicioMes)
                    .getSingleResult();
                return perdas != null ? perdas.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        });
    }

    public byte[] exportarVendasCsv(LocalDate inicio, LocalDate fim, String metodo, String estado, String q, Long usuarioId) {
        List<Venda> vendas = vendaDAO.listarParaExportacao(usuarioId, inicio, fim, metodo, estado, q);
        Map<Long, String> clientesMap = new HashMap<>();
        try {
            for (Devedor d : devedorDAO.listarTodos(usuarioId)) {
                clientesMap.put(d.getId(), d.getNome());
            }
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append(util.CsvUtil.UTF8_BOM);
        sb.append("Número;Data / Hora;Estado;Total (MZN);Custo Total (MZN);Lucro Estimado (MZN);Métodos de Pagamento;Cliente;Observação;Itens Vendidos\r\n");

        for (Venda v : vendas) {
            String numero = v.getNumero() != null ? String.valueOf(v.getNumero()) : String.valueOf(v.getId());
            String dataHora = util.CsvUtil.formatarDataHora(v.getCriadaEm());
            String est = v.getEstado() != null ? v.getEstado() : "CONCLUIDA";
            String total = util.CsvUtil.formatarMoeda(v.getTotal());
            String custo = util.CsvUtil.formatarMoeda(v.getTotalCusto());
            BigDecimal lucroCalc = "CONCLUIDA".equalsIgnoreCase(est)
                ? (v.getTotal() != null && v.getTotalCusto() != null ? v.getTotal().subtract(v.getTotalCusto()) : BigDecimal.ZERO)
                : BigDecimal.ZERO;
            String lucro = util.CsvUtil.formatarMoeda(lucroCalc);

            StringBuilder pgs = new StringBuilder();
            if (v.getPagamentos() != null) {
                for (PagamentoVenda pv : v.getPagamentos()) {
                    if (pgs.length() > 0) pgs.append(" | ");
                    String mNome = pv.getMetodoPagamento() != null ? pv.getMetodoPagamento().getNome() : pv.getMetodo();
                    pgs.append(mNome).append(": ").append(util.CsvUtil.formatarMoeda(pv.getValor())).append(" MZN");
                }
            }

            String cliente = "Consumidor Final";
            if (v.getClienteId() != null && clientesMap.containsKey(v.getClienteId())) {
                cliente = clientesMap.get(v.getClienteId());
            }

            String obs = v.getObservacao() != null ? v.getObservacao() : "";

            StringBuilder its = new StringBuilder();
            if (v.getItens() != null) {
                for (ItemVenda iv : v.getItens()) {
                    if (its.length() > 0) its.append(" | ");
                    String pNome = iv.getProduto() != null ? iv.getProduto().getNome() : "Item";
                    its.append(util.CsvUtil.formatarQuantidade(iv.getQuantidade()))
                       .append("x ")
                       .append(pNome)
                       .append(" (")
                       .append(util.CsvUtil.formatarMoeda(iv.getTotal()))
                       .append(" MZN)");
                }
            }

            sb.append(util.CsvUtil.escape(numero)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(dataHora)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(est)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(total)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(custo)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(lucro)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(pgs.toString())).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(cliente)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(obs)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(its.toString())).append("\r\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportarStockCsv(Long usuarioId) {
        List<Produto> produtos = produtoDAO.listarOrdenado(usuarioId, true);

        StringBuilder sb = new StringBuilder();
        sb.append(util.CsvUtil.UTF8_BOM);
        sb.append("ID;Código de Barras;Nome do Produto;Categoria;Unidade;Preço de Venda (MZN);Preço de Custo (MZN);Stock Atual;Stock Mínimo;Stock Máximo;Valor Total a Custo (MZN);Valor Total a Venda (MZN);Margem Unitária (MZN);Margem Unitária (%);Estado de Reposição;Ativo\r\n");

        for (Produto p : produtos) {
            String id = String.valueOf(p.getId());
            String codigoBarras = p.getCodigoBarras() != null ? p.getCodigoBarras() : "";
            String nome = p.getNome() != null ? p.getNome() : "";
            String categoria = p.getCategoria() != null ? p.getCategoria().getNome() : "Geral";
            String unidade = p.getUnidade() != null ? p.getUnidade() : "un";
            BigDecimal preco = p.getPreco() != null ? p.getPreco() : BigDecimal.ZERO;
            BigDecimal custo = p.getCusto() != null ? p.getCusto() : BigDecimal.ZERO;
            BigDecimal stock = p.getQuantidadeStock() != null ? p.getQuantidadeStock() : BigDecimal.ZERO;
            BigDecimal min = p.getStockMinimo() != null ? p.getStockMinimo() : BigDecimal.ZERO;
            BigDecimal max = p.getStockMaximo();

            BigDecimal totalCusto = stock.multiply(custo);
            BigDecimal totalVenda = stock.multiply(preco);
            BigDecimal margemMzn = preco.subtract(custo);
            BigDecimal margemPct = preco.compareTo(BigDecimal.ZERO) > 0
                ? margemMzn.multiply(new BigDecimal("100")).divide(preco, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String reposicao;
            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                reposicao = "Esgotado";
            } else if (stock.compareTo(min) <= 0) {
                reposicao = "Repor Urgente";
            } else if (max != null && stock.compareTo(max) > 0) {
                reposicao = "Excesso de Stock";
            } else {
                reposicao = "Normal";
            }

            String ativo = Boolean.TRUE.equals(p.getAtivo()) ? "Sim" : "Arquivado";

            sb.append(util.CsvUtil.escape(id)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(codigoBarras)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(nome)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(categoria)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(unidade)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarMoeda(preco))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarMoeda(custo))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarQuantidade(stock))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarQuantidade(min))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(max != null ? util.CsvUtil.formatarQuantidade(max) : "")).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarMoeda(totalCusto))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarMoeda(totalVenda))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarMoeda(margemMzn))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(util.CsvUtil.formatarPercentual(margemPct))).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(reposicao)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(ativo)).append("\r\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportarCaixaCsv(Long usuarioId, int limite) {
        List<SessaoCaixa> sessoes = sessaoCaixaDAO.listarHistorico(usuarioId, limite > 0 ? limite : 200);

        StringBuilder sb = new StringBuilder();
        sb.append(util.CsvUtil.UTF8_BOM);
        sb.append("ID Sessão;Estado;Data Abertura;Data Fecho;Fundo Inicial (MZN);Valor Esperado (MZN);Valor Contado (MZN);Diferença (MZN);Nota Abertura;Nota Fecho\r\n");

        for (SessaoCaixa s : sessoes) {
            String id = String.valueOf(s.getId());
            String estado = s.getEstado() != null ? s.getEstado() : "FECHADA";
            String abertaEm = util.CsvUtil.formatarDataHora(s.getAbertaEm());
            String fechadaEm = s.getFechadaEm() != null ? util.CsvUtil.formatarDataHora(s.getFechadaEm()) : "Em aberto";
            String inicial = util.CsvUtil.formatarMoeda(s.getValorInicial());
            String esperado = s.getValorEsperado() != null ? util.CsvUtil.formatarMoeda(s.getValorEsperado()) : "";
            String contado = s.getValorContado() != null ? util.CsvUtil.formatarMoeda(s.getValorContado()) : "";
            String diferenca = s.getDiferenca() != null ? util.CsvUtil.formatarMoeda(s.getDiferenca()) : "";
            String notaAb = s.getNotaAbertura() != null ? s.getNotaAbertura() : "";
            String notaFe = s.getNotaFecho() != null ? s.getNotaFecho() : "";

            sb.append(util.CsvUtil.escape(id)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(estado)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(abertaEm)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(fechadaEm)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(inicial)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(esperado)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(contado)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(diferenca)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(notaAb)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(notaFe)).append("\r\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportarDespesasCaixaCsv(Long sessaoId, Long usuarioId) {
        List<Map<String, Object>> despesas = listarDespesasCaixa(sessaoId, usuarioId);

        StringBuilder sb = new StringBuilder();
        sb.append(util.CsvUtil.UTF8_BOM);
        sb.append("ID;ID Sessão;Data / Hora;Categoria;Descrição;Valor (MZN)\r\n");

        for (Map<String, Object> d : despesas) {
            String id = String.valueOf(d.get("id"));
            String sId = d.get("sessaoId") != null ? String.valueOf(d.get("sessaoId")) : "-";
            String dataHora = d.get("criadaEm") != null ? String.valueOf(d.get("criadaEm")).replace('T', ' ').substring(0, Math.min(19, String.valueOf(d.get("criadaEm")).length())) : "";
            String cat = d.get("categoria") != null ? String.valueOf(d.get("categoria")) : "OUTRO";
            String desc = d.get("descricao") != null ? String.valueOf(d.get("descricao")) : "";
            String val = "0,00";
            if (d.get("valor") != null) {
                try {
                    val = util.CsvUtil.formatarMoeda(new BigDecimal(d.get("valor").toString()));
                } catch (Exception ignored) {
                    val = String.valueOf(d.get("valor")).replace('.', ',');
                }
            }

            sb.append(util.CsvUtil.escape(id)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(sId)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(dataHora)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(cat)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(desc)).append(util.CsvUtil.DELIMITER)
              .append(util.CsvUtil.escape(val)).append("\r\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Long longValue(Object value, String campo) {
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um identificador válido.");
        }
    }

    private static void validarProduto(String nome, BigDecimal preco, BigDecimal stock) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do produto não pode estar vazio.");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("O preço deve ser um valor não negativo.");
        }
        if (stock == null || stock.signum() < 0) {
            throw new IllegalArgumentException("O stock não pode ser negativo.");
        }
        moeda(preco);
        quantidade(stock);
    }

    public static BigDecimal moeda(BigDecimal valor) {
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("Os valores monetários aceitam no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantidade(BigDecimal valor) {
        if (valor.scale() > 3) {
            throw new IllegalArgumentException("As quantidades aceitam no máximo três casas decimais.");
        }
        return valor.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal stockMinimo(BigDecimal valor) {
        BigDecimal normalizado = valor == null ? new BigDecimal("5.000") : quantidade(valor);
        if (normalizado.signum() < 0) {
            throw new IllegalArgumentException("O stock mínimo não pode ser negativo.");
        }
        return normalizado;
    }

    private static String normalizarCodigoBarras(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return null;
        }
        if (codigo.trim().length() > 100) {
            throw new IllegalArgumentException("O código de barras é demasiado longo.");
        }
        return codigo.trim();
    }

    private static String decimalJson(BigDecimal valor, int escala) {
        return valor == null ? "0.00" : valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    public static final class RecursoNaoEncontradoException extends RuntimeException {
        public RecursoNaoEncontradoException(String message) { super(message); }
    }

    public static class ConflitoException extends RuntimeException {
        public ConflitoException(String message) { super(message); }
    }
}
