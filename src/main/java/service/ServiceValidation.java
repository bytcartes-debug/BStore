package service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ServiceValidation {

    private ServiceValidation() {}

    public static BigDecimal moeda(BigDecimal valor) {
        if (valor == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("Os valores monetários aceitam no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantidade(BigDecimal valor) {
        if (valor == null) return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        if (valor.scale() > 3) {
            throw new IllegalArgumentException("As quantidades aceitam no máximo três casas decimais.");
        }
        return valor.setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal stockMinimo(BigDecimal valor) {
        BigDecimal normalizado = valor == null ? new BigDecimal("5.000") : quantidade(valor);
        if (normalizado.signum() < 0) {
            throw new IllegalArgumentException("O stock mínimo não pode ser negativo.");
        }
        return normalizado;
    }

    public static String normalizarCodigoBarras(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return null;
        }
        if (codigo.trim().length() > 100) {
            throw new IllegalArgumentException("O código de barras é demasiado longo.");
        }
        return codigo.trim();
    }

    public static void validarProduto(String nome, BigDecimal preco, BigDecimal stock) {
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

    public static Long longValue(Object value, String campo) {
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um identificador válido.");
        }
    }

    public static String decimalJson(BigDecimal valor, int escala) {
        return valor == null ? "0.00" : valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }
}
