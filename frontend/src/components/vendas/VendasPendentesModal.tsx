import React from 'react';
import { CloudUpload, Trash2, WifiOff, AlertCircle, CheckCircle2 } from 'lucide-react';
import { Modal, Spinner } from '../UI';
import { formatMoney } from '../../utils/decimal';
import type { VendaPendente } from '../../utils/offlineQueue';

interface VendasPendentesModalProps {
  vendas: VendaPendente[];
  isOnline: boolean;
  isSincronizando: boolean;
  onSincronizar: () => void;
  onDescartar: (uuid: string) => void;
  onClose: () => void;
}

export const VendasPendentesModal: React.FC<VendasPendentesModalProps> = ({
  vendas,
  isOnline,
  isSincronizando,
  onSincronizar,
  onDescartar,
  onClose,
}) => {
  const totalPendente = vendas.reduce((acc, v) => {
    const val = parseFloat(v.total || '0');
    return acc + (isNaN(val) ? 0 : val);
  }, 0);

  return (
    <Modal title="Vendas Pendentes de Sincronização (Offline)" onClose={onClose} busy={isSincronizando}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Banner de status */}
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: isOnline ? '#f0fdf4' : '#fffbeb',
            border: `1px solid ${isOnline ? '#bbf7d0' : '#fef08a'}`,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {isOnline ? (
              <CheckCircle2 size={20} color="#16a34a" />
            ) : (
              <WifiOff size={20} color="#d97706" />
            )}
            <div>
              <div style={{ fontWeight: 600, fontSize: 13, color: isOnline ? '#166534' : '#92400e' }}>
                {isOnline ? 'Ligação à internet ativa' : 'Aparelho sem ligação à internet'}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                {vendas.length} venda(s) guardadas localmente • Total: {formatMoney(totalPendente.toFixed(2))}
              </div>
            </div>
          </div>

          {isOnline && vendas.length > 0 && (
            <button
              type="button"
              className="btn-primary"
              onClick={onSincronizar}
              disabled={isSincronizando}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, padding: '6px 12px' }}
            >
              {isSincronizando ? <Spinner size="small" /> : <CloudUpload size={14} />}
              Sincronizar Todas
            </button>
          )}
        </div>

        {/* Lista de vendas */}
        {vendas.length === 0 ? (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted, #64748b)' }}>
            Nenhuma venda pendente no aparelho. Todas as vendas estão sincronizadas com o servidor.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 360, overflowY: 'auto' }}>
            {vendas.map((v) => (
              <div
                key={v.uuidCliente}
                style={{
                  padding: '12px 14px',
                  borderRadius: 6,
                  border: '1px solid var(--border-color, #e2e8f0)',
                  background: 'var(--bg-secondary, #f8fafc)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 12,
                }}
              >
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <span style={{ fontWeight: 700, fontSize: 14 }}>
                      {v.numeroProvisorio || 'Venda Offline'}
                    </span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted, #64748b)' }}>
                      {new Date(v.dataCriacao).toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit' })}
                    </span>
                    {v.tentativas && v.tentativas > 0 && (
                      <span className="badge" style={{ fontSize: 10, background: '#fee2e2', color: '#991b1b' }}>
                        {v.tentativas} tentativa(s)
                      </span>
                    )}
                  </div>

                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    {v.itensQtd} item(ns) • Método: {v.metodos || 'Dinheiro'}
                    {v.resumoItens && ` (${v.resumoItens})`}
                  </div>

                  {v.erro && (
                    <div
                      style={{
                        marginTop: 4,
                        fontSize: 11,
                        color: '#b91c1c',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <AlertCircle size={12} />
                      {v.erro}
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--color-brand)' }}>
                    {formatMoney(v.total)}
                  </span>
                  <button
                    type="button"
                    className="icon-btn"
                    onClick={() => {
                      if (window.confirm('Descartar esta venda offline? Esta ação não pode ser desfeita.')) {
                        onDescartar(v.uuidCliente);
                      }
                    }}
                    title="Descartar venda pendente"
                    style={{ color: 'var(--color-danger, #ef4444)', padding: 4 }}
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
          <button type="button" className="btn-secondary" onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </Modal>
  );
};
