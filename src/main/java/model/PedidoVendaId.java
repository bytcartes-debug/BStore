package model;

import java.io.Serializable;
import java.util.Objects;

public class PedidoVendaId implements Serializable {
    private Long usuarioId;
    private String chave;

    public PedidoVendaId() {}
    public PedidoVendaId(Long usuarioId, String chave) { this.usuarioId = usuarioId; this.chave = chave; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PedidoVendaId)) return false;
        PedidoVendaId that = (PedidoVendaId) other;
        return Objects.equals(usuarioId, that.usuarioId) && Objects.equals(chave, that.chave);
    }

    @Override
    public int hashCode() { return Objects.hash(usuarioId, chave); }
}
