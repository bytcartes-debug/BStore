package service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.PedidoVenda;
import model.PedidoVendaId;
import util.JPAUtil;
import util.VendaJson;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VendaIdempotenteService {
    private final BarracaService service = new BarracaService();
    private final ObjectMapper mapper = new ObjectMapper();

    public Resultado registar(List<Map<String, Object>> itens, Long usuarioId, String chave) {
        return registar(itens, null, null, null, usuarioId, chave);
    }

    public Resultado registar(List<Map<String, Object>> itens,
                             List<Map<String, Object>> pagamentos,
                             Long clienteId,
                             String observacao,
                             Long usuarioId,
                             String chave) {
        return registar(itens, pagamentos, clienteId, observacao, null, usuarioId, chave);
    }

    public Resultado registar(List<Map<String, Object>> itens,
                             List<Map<String, Object>> pagamentos,
                             Long clienteId,
                             String observacao,
                             String uuidCliente,
                             Long usuarioId,
                             String chave) {
        if (chave == null || !chave.matches("[A-Za-z0-9_-]{8,80}")) {
            throw new IllegalArgumentException("Idempotency-Key deve ter entre 8 e 80 letras, números, hífen ou underscore.");
        }
        Map<Long, BigDecimal> quantidades = BarracaService.normalizarCarrinho(itens);
        String fingerprint = fingerprint(itens, pagamentos, clienteId);
        PedidoVendaId id = new PedidoVendaId(usuarioId, chave);
        try {
            return JPAUtil.emTransacao(usuarioId, em -> {
                PedidoVenda existente = em.find(PedidoVenda.class, id);
                if (existente != null) return repetir(existente, fingerprint);
                PedidoVenda pedido = new PedidoVenda(usuarioId, chave, fingerprint);
                em.persist(pedido);
                // A chave única é reservada antes de bloquear ou descontar qualquer produto.
                em.flush();
                Map<String, Object> venda = service.executarVendaLote(em, itens, pagamentos, clienteId, observacao, uuidCliente != null ? uuidCliente : chave, usuarioId);
                String json = serializar(VendaJson.lote(venda));
                pedido.setResposta(json);
                return new Resultado(json, false);
            });
        } catch (RuntimeException e) {
            if (!chaveDuplicada(e)) throw e;
            // A transação concorrente já confirmou; a transação que violou a chave foi revertida.
            return JPAUtil.emTransacao(usuarioId, em -> {
                PedidoVenda pedido = em.find(PedidoVenda.class, id);
                if (pedido == null) throw e;
                return repetir(pedido, fingerprint);
            });
        }
    }

    private Resultado repetir(PedidoVenda pedido, String fingerprint) {
        if (!pedido.getFingerprint().equals(fingerprint)) {
            throw new ChaveReutilizadaException();
        }
        if (pedido.getResposta().isEmpty()) throw new IllegalStateException("A venda ainda não tem resultado confirmado.");
        return new Resultado(pedido.getResposta(), true);
    }

    private boolean chaveDuplicada(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException) {
                SQLException sqlEx = (SQLException) cause;
                if ("23505".equals(sqlEx.getSQLState())) {
                    String msg = sqlEx.getMessage();
                    if (msg == null) msg = "";
                    String lower = msg.toLowerCase();
                    if (lower.contains("pedidos_venda") || lower.contains("uq_vendas_uuid") || lower.contains("uuid_cliente") || lower.contains("primary_key_fc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String serializar(Map<String, Object> resultado) {
        try { return mapper.writeValueAsString(resultado); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Não foi possível preparar o resultado da venda.", e); }
    }

    private String fingerprint(List<Map<String, Object>> itens, List<Map<String, Object>> pagamentos, Long clienteId) {
        StringBuilder canonical = new StringBuilder();
        if (itens != null) {
            List<String> linhas = new ArrayList<>();
            for (Map<String, Object> it : itens) {
                if (it == null) continue;
                Object pidObj = it.get("produtoId") != null ? it.get("produtoId") : it.get("id");
                String pid = pidObj != null ? pidObj.toString() : "0";
                BigDecimal qtd = BarracaService.decimal(it.get("quantidade"), "quantidade");
                String descP = it.get("descontoPercentual") != null ? it.get("descontoPercentual").toString().trim() : "0";
                String descV = it.get("descontoValor") != null ? it.get("descontoValor").toString().trim() : "0";
                String precoF = it.get("precoFinal") != null ? it.get("precoFinal").toString().trim() : "";
                String nota = it.get("nota") != null ? it.get("nota").toString().trim() : "";
                linhas.add(pid + ":" + qtd.setScale(3).toPlainString() + ":dp=" + descP + ":dv=" + descV + ":pf=" + precoF + ":n=" + nota);
            }
            java.util.Collections.sort(linhas);
            for (String l : linhas) canonical.append(l).append(';');
        }
        if (pagamentos != null) {
            List<String> list = new ArrayList<>();
            for (Map<String, Object> p : pagamentos) {
                if (p == null) continue;
                String met = p.get("metodo") != null ? p.get("metodo").toString().trim().toUpperCase() : "DINHEIRO";
                String val = p.get("valor") != null ? p.get("valor").toString() : "0";
                list.add(met + ":" + val);
            }
            java.util.Collections.sort(list);
            for (String s : list) canonical.append(s).append(';');
        }
        if (clienteId != null) {
            canonical.append("c:").append(clienteId).append(';');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static final class ChaveReutilizadaException extends BarracaService.ConflitoException {
        public ChaveReutilizadaException() { super("Esta chave de venda já foi usada com outro carrinho. Contacte o administrador para verificar o pedido."); }
    }

    public static final class Resultado {
        public final String json;
        public final boolean repetido;
        Resultado(String json, boolean repetido) { this.json = json; this.repetido = repetido; }
    }
}
