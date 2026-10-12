package service;

import dao.DevedorDAO;
import dao.MetodoPagamentoDAO;
import dao.ProdutoDAO;
import dao.SessaoCaixaDAO;
import dao.VendaDAO;
import model.Devedor;
import model.ItemVenda;
import model.MetodoPagamento;
import model.PagamentoVenda;
import model.Produto;
import model.SessaoCaixa;
import model.Venda;
import service.VendaLoteResultado.LinhaCalculada;
import service.VendaLoteResultado.PagamentoCalc;
import service.VendaLoteResultado.PrePagamento;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static service.ServiceValidation.*;

public class ProcessadorVendaLote {

    private final VendaDAO vendaDAO = new VendaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final MetodoPagamentoDAO metodoPagamentoDAO = new MetodoPagamentoDAO();
    private final SessaoCaixaDAO sessaoCaixaDAO = new SessaoCaixaDAO();

    public Map<String, Object> executar(EntityManager em,
                                       List<Map<String, Object>> itensRaw,
                                       List<Map<String, Object>> pagamentosRaw,
                                       Long clienteId,
                                       String observacao,
                                       String uuidCliente,
                                       Long usuarioId) {
        if (uuidCliente != null && !uuidCliente.trim().isEmpty()) {
            Venda existente = vendaDAO.buscarPorUuidCliente(em, uuidCliente.trim(), usuarioId);
            if (existente != null) {
                return VendaLoteResultado.montarResultadoExistente(existente);
            }
        }

        if (itensRaw == null || itensRaw.isEmpty()) {
            throw new IllegalArgumentException("Carrinho está vazio.");
        }

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
            if (produto == null) throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
            if (!produto.isAtivo()) {
                throw new BarracaService.ConflitoException("O produto " + produto.getNome() + " está arquivado e não pode ser vendido.");
            }
            if (produto.getQuantidadeStock().compareTo(entry.getValue()) < 0) {
                throw new BarracaService.ConflitoException("Stock insuficiente para " + produto.getNome() + ".");
            }
            produtos.put(entry.getKey(), produto);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalCusto = BigDecimal.ZERO;

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

        metodoPagamentoDAO.garantirMetodosPadrao(em, usuarioId);

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
            List<PrePagamento> preList = new ArrayList<>();
            BigDecimal somaOutros = BigDecimal.ZERO;
            PrePagamento fiadoSemValor = null;

            for (Map<String, Object> p : pagamentosRaw) {
                if (p == null) continue;
                MetodoPagamento mp = resolverMetodoPagamento(em, p, usuarioId);
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
                throw new BarracaService.RecursoNaoEncontradoException("Cliente não encontrado.");
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

        Long numero = vendaDAO.proximoNumero(em, usuarioId);
        Venda venda = new Venda(usuarioId, numero, util.Fuso.agora(), total, totalCusto, "CONCLUIDA", clienteId, observacao, usuarioId);
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

        List<ItemVenda> itensCriados = new ArrayList<>();
        for (LinhaCalculada lc : linhas) {
            ItemVenda iv = new ItemVenda(venda, lc.produto, lc.quantidade, lc.precoUnitario,
                lc.descontoPercentual, lc.descontoValor, lc.precoFinal,
                lc.produto.getCusto() != null ? lc.produto.getCusto() : BigDecimal.ZERO,
                observacao, lc.nota, usuarioId);
            em.persist(iv);
            itensCriados.add(iv);
            venda.addItem(iv);
        }

        StockService stockService = new StockService();
        for (Map.Entry<Long, BigDecimal> entry : quantidadesPorProduto.entrySet()) {
            Produto produto = produtos.get(entry.getKey());
            stockService.registarMovimento(em, usuarioId, produto.getId(), "VENDA",
                entry.getValue().negate(), produto.getCusto(), "Venda #" + numero,
                "VENDA", venda.getId(), usuarioId);
        }

        BigDecimal trocoRestante = troco;
        for (PagamentoCalc pc : pagamentosProcessados) {
            BigDecimal trocoDoItem = BigDecimal.ZERO;
            if ("DINHEIRO".equalsIgnoreCase(pc.metodoTipo) && trocoRestante.compareTo(BigDecimal.ZERO) > 0) {
                trocoDoItem = trocoRestante;
                trocoRestante = BigDecimal.ZERO;
            }
            String metodoCodigo = VendaLoteResultado.converterMetodoCodigo(pc.metodoNome, pc.metodoTipo);
            PagamentoVenda pv = new PagamentoVenda(usuarioId, venda, metodoCodigo, pc.metodoPagamento, pc.referencia, pc.valor, trocoDoItem);
            em.persist(pv);
            venda.addPagamento(pv);
        }
        em.flush();

        return VendaLoteResultado.montarResultadoSucesso(venda, total, totalCusto, troco, itensCriados);
    }

    private MetodoPagamento resolverMetodoPagamento(EntityManager em, Map<String, Object> p, Long usuarioId) {
        if (p.get("metodoId") != null) {
            Long mid = longValue(p.get("metodoId"), "metodoId");
            MetodoPagamento mp = metodoPagamentoDAO.buscarPorId(em, mid, usuarioId);
            if (mp == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            return mp;
        } else if (p.get("metodo") != null) {
            String mStr = p.get("metodo").toString().trim();
            MetodoPagamento mp = metodoPagamentoDAO.buscarPorNome(em, mStr, usuarioId);
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
            return mp;
        } else {
            throw new IllegalArgumentException("Método de pagamento não informado.");
        }
    }
}
