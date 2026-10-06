package dao;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Pagina<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int pageSize;

    public Pagina(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotalPages() { return Math.max(1, (total + pageSize - 1) / pageSize); }

    public <R> Pagina<R> map(Function<T, R> mapper) {
        return new Pagina<>(items.stream().map(mapper).collect(Collectors.toList()), total, page, pageSize);
    }

    public static int offset(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || ((long) page - 1) * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Paginação inválida. Use páginas positivas e entre 1 e 100 registos por página.");
        }
        return (page - 1) * pageSize;
    }

    public static String pesquisa(String value) {
        if (value == null) return "";
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.length() > 150) throw new IllegalArgumentException("A pesquisa aceita até 150 caracteres.");
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
