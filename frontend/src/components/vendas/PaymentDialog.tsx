import React, { useEffect } from 'react';
import type Decimal from 'decimal.js';
import { AlertTriangle, Check, CreditCard, Delete, Plus, Trash2, User } from 'lucide-react';
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

const PaymentDialogComponent: React.FC<PaymentDialogProps> = ({
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
  // Atalhos de teclado no PC: números 1 a 5 escolhem método de pagamento
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const activeEl = document.activeElement;
      if (
        activeEl instanceof HTMLInputElement &&
        (activeEl.type === 'text' || activeEl.id === 'sale-obs')
      ) {
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
        return;
      }
      const num = parseInt(e.key, 10);
      if (!isNaN(num) && num >= 1 && num <= metodosDisponiveis.length) {
        const metodoEscolhido = metodosDisponiveis[num - 1];
        if (metodoEscolhido) {
          e.preventDefault();
          onSelecionarMetodoPrincipal(metodoEscolhido);
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [metodosDisponiveis, onSelecionarMetodoPrincipal, onCancel]);

  // Teclado tátil na página para a linha de pagamento principal
  const handleKeypadPress = (key: string) => {
    const targetLine = pagamentos[0];
    if (!targetLine || targetLine.metodo === 'FIADO') return;
    const currentVal = targetLine.valor || '';

    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(30);
      } catch {}
    }

    if (key === 'CLEAR') {
      onUpdateLinhaPagamento(targetLine.id, 'valor', '');
    } else if (key === 'BACKSPACE') {
      onUpdateLinhaPagamento(targetLine.id, 'valor', currentVal.slice(0, -1));
    } else if (key === '.') {
      if (!currentVal.includes('.')) {
        onUpdateLinhaPagamento(targetLine.id, 'valor', currentVal ? `${currentVal}.` : '0.');
      }
    } else {
      const nextVal = currentVal === '0' ? key : `${currentVal}${key}`;
      onUpdateLinhaPagamento(targetLine.id, 'valor', nextVal);
    }
  };

  return (
    <div
      style={{
        marginTop: 20,
        borderTop: '2px solid var(--border-color)',
        paddingTop: 16,
      }}
    >
      {/* TOTAL DO CARRINHO EM DESTAQUE */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 16,
          padding: '14px 18px',
          borderRadius: 10,
          background: 'var(--bg-subtle)',
          border: '1px solid var(--border-color)',
        }}
        aria-live="polite"
      >
        <span style={{ fontSize: 16, fontWeight: 650 }}>Total do Carrinho:</span>
        <strong
          style={{
            fontSize: 'clamp(28px, 4vw, 36px)',
            color: 'var(--color-brand)',
            letterSpacing: '-0.5px',
          }}
          aria-live="polite"
        >
          {formatMoney(total)}
        </strong>
      </div>

      {/* BOTÕES TOUCH DE MÉTODOS ACTIVOS */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontSize: 13, fontWeight: 600 }}>
          Forma de pagamento rápida:
        </span>
        <span className="desktop-only-hint" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
          (Atalhos 1 a {metodosDisponiveis.length} no teclado)
        </span>
      </div>
      <div className="payment-methods-grid">
        {metodosDisponiveis.map((m, idx) => {
          const isSelected =
            pagamentos[0]?.metodo.toUpperCase() === m.nome.toUpperCase() ||
            (m.tipo === 'FIADO' && pagamentos[0]?.metodo.toUpperCase() === 'FIADO');
          return (
            <button
              key={m.id}
              type="button"
              className={`payment-method-tile ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelecionarMetodoPrincipal(m)}
              aria-pressed={isSelected}
            >
              <CreditCard size={18} />
              <span>{m.nome}</span>
              <span className="tile-shortcut" aria-hidden="true">[{idx + 1}]</span>
            </button>
          );
        })}
      </div>

      {/* ATALHOS DE NOTAS PARA DINHEIRO */}
      {temDinheiro && (
        <div style={{ marginBottom: 14 }}>
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

      {/* LINHAS DE PAGAMENTO DETALHADAS */}
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
                inputMode="decimal"
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
                    padding: '8px 10px',
                    fontSize: 13,
                    borderRadius: 6,
                    border: '1px solid var(--border-color)',
                  }}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* TECLADO NUMÉRICO TÁTIL INTEGRADO NA PÁGINA */}
      {pagamentos[0]?.metodo !== 'FIADO' && (
        <div className="pos-keypad-container" aria-label="Teclado numérico da página">
          <div className="pos-keypad">
            {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
              <button
                key={digit}
                type="button"
                className="pos-keypad-btn"
                onClick={() => handleKeypadPress(digit)}
              >
                {digit}
              </button>
            ))}
            <button
              type="button"
              className="pos-keypad-btn"
              onClick={() => handleKeypadPress('.')}
            >
              ,
            </button>
            <button
              type="button"
              className="pos-keypad-btn"
              onClick={() => handleKeypadPress('0')}
            >
              0
            </button>
            <button
              type="button"
              className="pos-keypad-btn action-clear"
              onClick={() => handleKeypadPress('BACKSPACE')}
              title="Apagar último caractere"
              aria-label="Apagar dígito"
            >
              <Delete size={20} />
            </button>
          </div>
        </div>
      )}

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
              fontWeight: 650,
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
                padding: 10,
                borderRadius: 6,
                border: '1px solid var(--input-border)',
                fontSize: 14,
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
                  padding: 10,
                  borderRadius: 6,
                  border: '1px solid var(--input-border)',
                  fontSize: 14,
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

          {/* ALERTA DE SALDO ACUMULADO DO DEVEDOR */}
          {(() => {
            const clienteSel = devedores.find((d) => String(d.id) === String(clienteId));
            if (!clienteSel || !clienteSel.saldo || Number(clienteSel.saldo) <= 0) return null;
            const saldoAtual = Number(clienteSel.saldo);
            const novoSaldoTotal = saldoAtual + total.toNumber();
            return (
              <div
                style={{
                  marginTop: 10,
                  padding: '10px 12px',
                  borderRadius: 6,
                  background: 'var(--color-warning-light)',
                  border: '1px solid var(--color-warning)',
                  color: 'var(--text-primary)',
                  fontSize: 12.5,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <AlertTriangle size={18} color="var(--color-warning)" style={{ flexShrink: 0 }} />
                <span>
                  <strong>Atenção ao Fiado:</strong> {clienteSel.nome} já tem dívida de <strong>{formatMoney(saldoAtual)}</strong>. Com esta venda, a dívida total subirá para <strong>{formatMoney(novoSaldoTotal)}</strong>.
                </span>
              </div>
            );
          })()}
        </div>
      )}

      {/* RESUMO DE VALORES E TROCO EM TEMPO REAL */}
      <div style={{ marginTop: 16 }} aria-live="polite">
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

      {/* OBSERVAÇÃO */}
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
            padding: 10,
            borderRadius: 6,
            border: '1px solid var(--border-color)',
            fontSize: 14,
          }}
        />
      </div>

      {/* BOTÕES DE CONFIRMAÇÃO FIXOS E ACESSÍVEIS */}
      <div className="modal-actions" style={{ marginTop: 24, gap: 12 }}>
        <button type="button" className="btn-secondary" onClick={onCancel} style={{ minHeight: 48, fontSize: 15 }}>
          Cancelar
        </button>
        <button
          className="btn-primary"
          type="submit"
          disabled={!canSubmit || isSaving}
          style={{ minHeight: 48, fontSize: 16, flex: 1 }}
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

export const PaymentDialog = React.memo(PaymentDialogComponent);

