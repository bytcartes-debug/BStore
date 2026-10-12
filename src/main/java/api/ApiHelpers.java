package api;

import io.javalin.http.Context;
import model.Usuario;
import service.BarracaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiHelpers {

    public static final DateTimeFormatter DATA_FORMATADA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @SuppressWarnings("unchecked")
    public static Map<String, Object> corpo(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null) {
            throw new IllegalArgumentException("Corpo do pedido inválido.");
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> itensVenda(Context ctx) {
        List<?> raw = ctx.bodyAsClass(List.class);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Carrinho está vazio.");
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Cada item da venda deve ser um objecto.");
            }
            itens.add((Map<String, Object>) item);
        }
        return itens;
    }

    public static String queryPadrao(Context ctx, String campo, String padrao) {
        String valor = ctx.queryParam(campo);
        return valor == null ? padrao : valor;
    }

    public static int inteiroQuery(Context ctx, String campo, int padrao) {
        try {
            return Integer.parseInt(queryPadrao(ctx, campo, Integer.toString(padrao)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parâmetro " + campo + " inválido.");
        }
    }

    public static Long longQuery(Context ctx, String campo) {
        String valor = ctx.queryParam(campo);
        if (valor == null || valor.isBlank()) return null;
        try {
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parâmetro " + campo + " inválido.");
        }
    }

    public static LocalDate dataQuery(Context ctx, String campo) {
        String valor = ctx.queryParam(campo);
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDate.parse(valor);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Use uma data válida no formato AAAA-MM-DD.");
        }
    }

    public static Long idPath(Context ctx) {
        try {
            return Long.valueOf(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificador inválido.");
        }
    }

    public static Long longObrigatorio(Map<String, Object> body, String campo) {
        Object value = body.get(campo);
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um identificador válido.");
        }
    }

    public static BigDecimal decimalObrigatorio(Map<String, Object> body, String campo) {
        return BarracaService.decimal(body.get(campo), campo);
    }

    public static BigDecimal decimalComPadrao(Map<String, Object> body, String campo, String padrao) {
        return body.get(campo) == null ? new BigDecimal(padrao) : decimalObrigatorio(body, campo);
    }

    public static int intComPadrao(Map<String, Object> body, String campo, int padrao) {
        Object value = body.get(campo);
        if (value == null) return padrao;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser inteiro.");
        }
    }

    public static String textoObrigatorio(Map<String, Object> body, String campo) {
        String value = texto(body, campo);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        return value.trim();
    }

    public static String textoComPadrao(Map<String, Object> body, String campo, String padrao) {
        String value = texto(body, campo);
        return value == null || value.isBlank() ? padrao : value;
    }

    public static String texto(Map<String, Object> body, String campo) {
        Object value = body.get(campo);
        if (value == null) return null;
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser texto.");
        }
        return (String) value;
    }

    public static BigDecimal dinheiro(BigDecimal valor) {
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("Os valores monetários aceitam no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static String decimalJson(BigDecimal valor, int escala) {
        return valor == null ? null : valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    public static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static void validarRole(String role) {
        if (!"superuser".equals(role) && !"operator".equals(role)) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
    }

    public static Map<String, Object> utilizadorJson(Usuario usuario) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", usuario.getId());
        resultado.put("nome", usuario.getNome());
        resultado.put("email", usuario.getEmail());
        resultado.put("role", usuario.getRole());
        resultado.put("diasAcesso", usuario.getDiasAcesso());
        resultado.put("dataExpiracao", usuario.getDataExpiracao() == null ? null : usuario.getDataExpiracao().format(DATA_FORMATADA));
        resultado.put("diasRestantes", usuario.diasRestantes());
        resultado.put("expirado", usuario.isExpirado());
        return resultado;
    }
}
