import React from 'react';
import type Decimal from 'decimal.js';
import { Check, CreditCard, Plus, Trash2, User } from 'lucide-react';
import { Field, Notice, Spinner } from '../UI';
import { decimalSeguro, formatMoney } from '../../utils/decimal';
import type { Devedor, LinhaPagamento, MetodoPagamentoConfig } from './types';

interface PaymentDialogProps {
  total: Decimal;
  metodosDisponiveis: MetodoPagamentoConfig[];
  pagamentos: LinhaPagamento[];
  temDinheiro: boolean;
  temFiado: boolean;
  onSelecionarMetodoPrincipal: (m: MetodoPagamentoConfig) => void;
  onUpdateLinhaPagamento: (id: string, field: 'metodo' | 'valor' | 'referencia', value: string) => void;
  onAddLinhaPagamento: () => void;
  onRemoveLinhaPagamento: (id: string) => void;
  devedores: Devedor[];
  clienteId: string;
  onClienteIdChange: (val: string) => void;
  criandoNovoCliente: boolean;
  onSetCriandoNovoCliente: React.Dispatch<React.SetStateAction<boolean>>;
  novoClienteNome: string;
  onNovoClienteNomeChange: (val: string) => void;
  onCriarClienteRapido: () => void;
  trocoCalculado: Decimal;
  faltaPagar: Decimal;
  observacao: string;
  onObservacaoChange: (val: string) => void;
  onCancel: () => void;
  canSubmit: boolean;
  isSaving: boolean;
}

export const PaymentDialog: React.FC<PaymentDialogProps> = ({
  total,
  metodosDisponiveis,
  pagamentos,
  temDinheiro,
  temFiado,
  onSelecionarMetodoPrincipal,
  onUpdateLinhaPagamento,
  onAddLinhaPagamento,
  onRemoveLinhaPagamento,
  devedores,
  clienteId,
  onClienteIdChange,
  criandoNovoCliente,
  onSetCriandoNovoCliente,
  novoClienteNome,
  onNovoClienteNomeChange,
  onCriarClienteRapido,
  trocoCalculado,
  faltaPagar,
  observacao,
  onObservacaoChange,
  onCancel,
  canSubmit,
  isSaving,
}) => {
  return (
    <div
      style={{
        marginTop: 24,
        borderTop: '2px solid var(--border-color)',
        paddingTop: 16,
      }}
    >
      {/* Total em destaque grande */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 16,
          padding: '12px 16px',
          borderRadius: 8,
          background: 'var(--bg-subtle)',
        }}
      >
        <span style={{ fontSize: 15, fontWeight: 600 }}>Total do Carrinho:</span>
        <strong
          style={{
            fontSize: 32,
            color: 'var(--color-brand)',
            letterSpacing: '-0.5px',
          }}
        >
          {formatMoney(total)}
        </strong>
      </div>

      {/* BOTÕES TOUCH DE MÉTODOS ACTIVOS */}
      <span
        style={{
          display: 'block',
          fontSize: 13,
          fontWeight: 600,
          marginBottom: 8,
        }}
      >
        Forma de pagamento rápida:
      </span>
      <div className="payment-methods-grid">
        {metodosDisponiveis.map((m) => {
          const isSelected =
            pagamentos[0]?.metodo.toUpperCase() === m.nome.toUpperCase() ||
            (m.tipo === 'FIADO' && pagamentos[0]?.metodo.toUpperCase() === 'FIADO');
          return (
            <button
              key={m.id}
              type="button"
              className={`payment-method-tile ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelecionarMetodoPrincipal(m)}
            >
              <CreditCard size={18} />
              <span>{m.nome}</span>
            </button>
          );
        })}
      </div>

      {/* ATALHOS DE NOTAS PARA DINHEIRO */}
      {temDinheiro && (
        <div>
          <span
            style={{
              display: 'block',
              fontSize: 12,
              color: 'var(--text-secondary)',
              marginBottom: 4,
            }}
          >
            Notas recebidas (atalho):
          </span>
          <div className="quick-cash-row">
            <button
              type="button"
              className="quick-cash-btn"
              onClick={() => onUpdateLinhaPagamento(pagamentos[0].id, 'valor', total.toFixed(2))}
            >
              Exato ({formatMoney(total)})
            </button>
            {[50, 100, 200, 500, 1000, 2000].map((valorNota) => (
              <button
                key={valorNota}
                type="button"
                className="quick-cash-btn"
                onClick={() =>
                  onUpdateLinhaPagamento(pagamentos[0].id, 'valor', valorNota.toString())
                }
              >
                {valorNota} MT
              </button>
            ))}
          </div>
        </div>
      )}

      {/* LINHAS DE PAGAMENTO DETALHADAS (DIVIDIR PAGAMENTO) */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          margin: '14px 0 8px',
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 600 }}>Divisão do pagamento:</span>
        <button
          type="button"
          className="btn-secondary"
          style={{ fontSize: 12, padding: '3px 8px' }}
          onClick={onAddLinhaPagamento}
        >
          <Plus size={13} /> Dividir
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {pagamentos.map((p, idx) => (
          <div key={p.id} className="sale-payment-split-row">
            <Field id={`metodo-${p.id}`} label={`Forma ${idx + 1}`}>
              <select
                id={`metodo-${p.id}`}
                value={p.metodo}
                onChange={(e) => onUpdateLinhaPagamento(p.id, 'metodo', e.target.value)}
              >
                {metodosDisponiveis.map((m) => (
                  <option
                    key={m.id}
                    value={m.tipo === 'FIADO' ? 'FIADO' : m.nome.toUpperCase()}
                  >
                    {m.nome}
                  </option>
                ))}
              </select>
            </Field>

            <Field
              id={`valor-${p.id}`}
              label={p.metodo === 'FIADO' ? 'Valor a fiado (calculado)' : 'Valor (MT)'}
            >
              <input
                id={`valor-${p.id}`}
                type="number"
                step="0.01"
                min="0.01"
                readOnly={p.metodo === 'FIADO'}
                value={
                  p.metodo === 'FIADO'
                    ? (() => {
                        const outros = pagamentos
                          .filter((x) => x.id !== p.id)
                          .reduce(
                            (acc, x) => acc.plus(decimalSeguro(x.valor)),
                            decimalSeguro(0),
                          );
                        const saldo = total.minus(outros);
                        return saldo.gt(0) ? saldo.toFixed(2) : '0.00';
                      })()
                    : p.valor
                }
                placeholder={pagamentos.length === 1 ? total.toFixed(2) : '0,00'}
                style={
                  p.metodo === 'FIADO'
                    ? { background: 'var(--bg-subtle, #f3f4f6)', fontWeight: 600 }
                    : undefined
                }
                onChange={(e) => onUpdateLinhaPagamento(p.id, 'valor', e.target.value)}
              />
            </Field>

            <button
              type="button"
              className="icon-btn delete"
              disabled={pagamentos.length <= 1}
              onClick={() => onRemoveLinhaPagamento(p.id)}
              style={{ marginBottom: 6 }}
              title="Remover linha"
              aria-label="Remover linha"
            >
              <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
            </button>

            {p.metodo !== 'DINHEIRO' && p.metodo !== 'FIADO' && (
              <div style={{ gridColumn: '1 / -1', marginTop: -4 }}>
                <input
                  type="text"
                  placeholder="Código / referência da transação (opcional)"
                  value={p.referencia || ''}
                  onChange={(e) =>
                    onUpdateLinhaPagamento(p.id, 'referencia', e.target.value)
                  }
                  style={{
                    width: '100%',
                    padding: '6px 8px',
                    fontSize: 12,
                    borderRadius: 6,
                    border: '1px solid var(--border-color)',
                  }}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* CAMPO DE CLIENTE CASO TENHA FIADO */}
      {temFiado && (
        <div
          style={{
            marginTop: 16,
            padding: 12,
            background: 'var(--bg-subtle)',
            borderRadius: 8,
            border: '1px solid var(--border-color)',
          }}
        >
          <label
            htmlFor="cliente-fiado"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontWeight: 600,
              marginBottom: 6,
            }}
          >
            <User size={15} strokeWidth={2} aria-hidden="true" /> Cliente Devedor
            (Obrigatório para Fiado):
          </label>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <select
              id="cliente-fiado"
              value={clienteId}
              onChange={(e) => onClienteIdChange(e.target.value)}
              style={{
                flex: 1,
                padding: 8,
                borderRadius: 6,
                border: '1px solid var(--input-border)',
              }}
              required
            >
              <option value="">Selecione o cliente…</option>
              {devedores.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.nome} {d.saldo ? `(Saldo: ${formatMoney(d.saldo)})` : ''}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => onSetCriandoNovoCliente((v) => !v)}
            >
              {criandoNovoCliente ? (
                'Cancelar'
              ) : (
                <>
                  <Plus size={14} strokeWidth={2} aria-hidden="true" /> Novo
                </>
              )}
            </button>
          </div>

          {criandoNovoCliente && (
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              <input
                type="text"
                placeholder="Nome do novo cliente"
                value={novoClienteNome}
                onChange={(e) => onNovoClienteNomeChange(e.target.value)}
                style={{
                  flex: 1,
                  padding: 8,
                  borderRadius: 6,
                  border: '1px solid var(--input-border)',
                }}
              />
              <button
                type="button"
                className="btn-primary"
                onClick={onCriarClienteRapido}
                disabled={!novoClienteNome.trim()}
              >
                Guardar
              </button>
            </div>
          )}
        </div>
      )}

      {/* RESUMO DE VALORES E TROCO EM TEMPO REAL */}
      <div style={{ marginTop: 16 }}>
        {trocoCalculado.gt(0) && (
          <Notice kind="success">
            Troco a devolver ao cliente: <strong>{formatMoney(trocoCalculado)}</strong>
          </Notice>
        )}
        {faltaPagar.gt(0) && !temFiado && (
          <Notice kind="error">
            Falta receber: <strong>{formatMoney(faltaPagar)}</strong>
          </Notice>
        )}
      </div>

      <div style={{ marginTop: 16 }}>
        <label
          htmlFor="sale-obs"
          style={{
            display: 'block',
            fontSize: 13,
            fontWeight: 600,
            marginBottom: 4,
          }}
        >
          Observações (Opcional):
        </label>
        <input
          id="sale-obs"
          type="text"
          value={observacao}
          onChange={(e) => onObservacaoChange(e.target.value)}
          placeholder="Ex: Entrega ao domicílio, cliente regular..."
          style={{
            width: '100%',
            padding: 8,
            borderRadius: 6,
            border: '1px solid var(--border-color, #ccc)',
          }}
        />
      </div>

      <div className="modal-actions" style={{ marginTop: 24 }}>
        <button type="button" className="btn-secondary" onClick={onCancel}>
          Cancelar
        </button>
        <button
          className="btn-primary"
          type="submit"
          disabled={!canSubmit || isSaving}
          style={{ minHeight: 48, fontSize: 16 }}
        >
          {isSaving ? (
            <Spinner size="small" />
          ) : (
            <Check size={18} strokeWidth={2.2} aria-hidden="true" />
          )}
          {isSaving ? ' A processar…' : ' Confirmar venda'}
        </button>
      </div>
    </div>
  );
};
