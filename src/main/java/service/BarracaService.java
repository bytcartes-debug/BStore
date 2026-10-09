package service;

import dao.CategoriaDAO;
import dao.DevedorDAO;
import dao.ItemVendaDAO;
import dao.MovimentoStockDAO;
import dao.PagamentoDividaDAO;
import dao.PagamentoVendaDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Categoria;
import model.Devedor;
import model.ItemVenda;
import model.MovimentoStock;
import model.PagamentoDivida;
import model.PagamentoVenda;
import model.Produto;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
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
        return JPAUtil.emTransacao(usuarioId, em -> {
            Produto produto = new Produto(nome.trim(), moeda(preco), stockNorm, unidade, categoria);
            produto.setCusto(custoNorm);
            produto.setAtivo(true);
            produto.setStockMinimo(stockMinimo(stockMinimo));
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
        return actualizarProduto(id, nome, preco, null, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
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
        return registarVendaLote(itens, null, null, null, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        Map<Long, BigDecimal> quantidades = normalizarCarrinho(itens);
        try {
            return JPAUtil.emTransacao(usuarioId, em -> executarVendaLote(em, quantidades, pagamentos, clienteId, observacao, usuarioId));
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
        Map<Long, Produto> produtos = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> item : quantidades.entrySet()) {
            Produto produto = produtoDAO.buscarPorIdParaBloqueio(em, item.getKey(), usuarioId);
            if (produto == null) throw new RecursoNaoEncontradoException("Produto não encontrado.");
            if (!produto.isAtivo()) {
                throw new ConflitoException("O produto " + produto.getNome() + " está arquivado e não pode ser vendido.");
            }
            if (produto.getQuantidadeStock().compareTo(item.getValue()) < 0) {
                throw new ConflitoException("Stock insuficiente para " + produto.getNome() + ".");
            }
            produtos.put(item.getKey(), produto);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalCusto = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> item : quantidades.entrySet()) {
            Produto produto = produtos.get(item.getKey());
            BigDecimal itemTotal = item.getValue().multiply(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal custoUnit = produto.getCusto() != null ? produto.getCusto() : BigDecimal.ZERO;
            BigDecimal itemCusto = item.getValue().multiply(custoUnit).setScale(2, RoundingMode.HALF_UP);
            total = total.add(itemTotal);
            totalCusto = totalCusto.add(itemCusto);
        }

        // Validação de pagamentos
        List<Map<String, Object>> pagamentosProcessados = new ArrayList<>();
        boolean temFiado = false;
        BigDecimal totalFiado = BigDecimal.ZERO;
        BigDecimal somaPagamentos = BigDecimal.ZERO;

        if (pagamentosRaw == null || pagamentosRaw.isEmpty()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("metodo", "DINHEIRO");
            p.put("valor", total);
            pagamentosProcessados.add(p);
            somaPagamentos = total;
        } else {
            for (Map<String, Object> p : pagamentosRaw) {
                if (p == null) continue;
                String metodo = p.get("metodo") != null ? p.get("metodo").toString().trim().toUpperCase() : "DINHEIRO";
                if (!List.of("DINHEIRO", "MPESA", "EMOLA", "MKESH", "CARTAO", "FIADO").contains(metodo)) {
                    throw new IllegalArgumentException("Método de pagamento inválido: " + metodo);
                }
                BigDecimal valor = moeda(decimal(p.get("valor"), "valor"));
                if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
                }
                if ("FIADO".equals(metodo)) {
                    temFiado = true;
                    totalFiado = totalFiado.add(valor);
                }
                somaPagamentos = somaPagamentos.add(valor);
                Map<String, Object> itemP = new LinkedHashMap<>();
                itemP.put("metodo", metodo);
                itemP.put("valor", valor);
                pagamentosProcessados.add(itemP);
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
            if (somaPagamentos.compareTo(total) < 0) {
                throw new IllegalArgumentException("A soma dos pagamentos e fiado é inferior ao total da venda.");
            }
        } else {
            if (somaPagamentos.compareTo(total) < 0) {
                throw new IllegalArgumentException("O valor total dos pagamentos é inferior ao total da venda.");
            }
        }

        BigDecimal troco = BigDecimal.ZERO;
        if (somaPagamentos.compareTo(total) > 0) {
            boolean temDinheiro = pagamentosProcessados.stream().anyMatch(p -> "DINHEIRO".equals(p.get("metodo")));
            if (!temDinheiro) {
                throw new IllegalArgumentException("Troco só é permitido para pagamentos em dinheiro.");
            }
            troco = somaPagamentos.subtract(total);
        }

        Long numero = vendaDAO.proximoNumero(em, usuarioId);
        Venda venda = new Venda(usuarioId, numero, LocalDateTime.now(), total, totalCusto, "CONCLUIDA", clienteId, observacao, usuarioId);
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

        List<ItemVenda> itensCriados = new ArrayList<>();
        StockService stockService = new StockService();
        for (Map.Entry<Long, BigDecimal> item : quantidades.entrySet()) {
            Produto produto = produtos.get(item.getKey());
            ItemVenda iv = new ItemVenda(venda, produto, item.getValue(), produto.getPreco(),
                produto.getCusto() != null ? produto.getCusto() : BigDecimal.ZERO, null, usuarioId);
            em.persist(iv);
            itensCriados.add(iv);
            venda.addItem(iv);

            stockService.registarMovimento(em, usuarioId, produto.getId(), "VENDA",
                item.getValue().negate(), produto.getCusto(), "Venda #" + numero,
                "VENDA", venda.getId(), usuarioId);
        }

        BigDecimal trocoRestante = troco;
        for (Map<String, Object> p : pagamentosProcessados) {
            String metodo = (String) p.get("metodo");
            BigDecimal valor = (BigDecimal) p.get("valor");
            BigDecimal trocoDoItem = BigDecimal.ZERO;
            if ("DINHEIRO".equals(metodo) && trocoRestante.compareTo(BigDecimal.ZERO) > 0) {
                trocoDoItem = trocoRestante;
                trocoRestante = BigDecimal.ZERO;
            }
            PagamentoVenda pv = new PagamentoVenda(usuarioId, venda, metodo, valor, trocoDoItem);
            em.persist(pv);
            venda.addPagamento(pv);
        }
        em.flush();

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", venda.getId());
        resultado.put("numero", venda.getNumero());
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
            Venda venda = em.find(Venda.class, vendaId);
            if (venda == null || !usuarioId.equals(venda.getUsuarioId())) {
                throw new RecursoNaoEncontradoException("Venda não encontrada.");
            }
            if ("ANULADA".equals(venda.getEstado())) {
                return venda; // Idempotente
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
        BigDecimal lucroEstimado = totalVendido.subtract(totalCusto);
        long numeroVendas = vendaDAO.contarVendasHoje(usuarioId);
        long vendasAnuladas = vendaDAO.contarVendasAnuladasHoje(usuarioId, dia);
        Map<String, BigDecimal> totaisMetodo = pagamentoVendaDAO.totaisPorMetodo(usuarioId, dia);
        BigDecimal totalFiado = totaisMetodo.getOrDefault("FIADO", BigDecimal.ZERO);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("data", dia.toString());
        res.put("totalVendido", decimalJson(totalVendido, 2));
        res.put("totalCusto", decimalJson(totalCusto, 2));
        res.put("lucroEstimado", decimalJson(lucroEstimado, 2));
        res.put("numeroVendas", numeroVendas);
        res.put("vendasAnuladas", vendasAnuladas);
        res.put("totalFiado", decimalJson(totalFiado, 2));
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
