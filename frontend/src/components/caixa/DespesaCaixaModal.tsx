import React, { useState, useEffect, useCallback } from 'react';
import { apiRequest } from '../../utils/api';
import { formatMoney, parseDecimalInput } from '../../utils/decimal';
import { Modal, Field, Notice, Spinner } from '../UI';
import { useToast } from '../../utils/toast';
import type { DespesaCaixa } from '../vendas/types';
import { DollarSign, Tag, FileText, ArrowDownRight, Clock } from 'lucide-react';

interface DespesaCaixaModalProps {
  onClose: () => void;
  onDespesaRegistada: () => void;
  sessaoId?: number;
}

const CATEGORIAS = [
  { id: 'ENERGIA', label: 'Energia / Credelec' },
  { id: 'TRANSPORTE', label: 'Transporte / Frete' },
  { id: 'ALIMENTACAO', label: 'Alimentação / Refeição' },
  { id: 'LIMPEZA', label: 'Limpeza e Higiene' },
  { id: 'OUTRO', label: 'Outro' },
];

export function DespesaCaixaModal({ onClose, onDespesaRegistada, sessaoId }: DespesaCaixaModalProps) {
  const toast = useToast();
  const [valor, setValor] = useState('');
  const [categoria, setCategoria] = useState('ENERGIA');
  const [descricao, setDescricao] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const [despesas, setDespesas] = useState<DespesaCaixa[]>([]);
  const [carregandoLista, setCarregandoLista] = useState(true);

  const carregarDespesas = useCallback(async () => {
    setCarregandoLista(true);
    try {
      const url = sessaoId ? `/api/caixa/despesas?sessaoId=${sessaoId}` : '/api/caixa/despesas';
      const res = await apiRequest<DespesaCaixa[]>(url);
      setDespesas(res || []);
    } catch {
      // Ignora erro de listagem
    } finally {
      setCarregandoLista(false);
    }
  }, [sessaoId]);

  useEffect(() => {
    void carregarDespesas();
  }, [carregarDespesas]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);

    const valDec = parseDecimalInput(valor);
    if (!valDec || valDec.lte(0)) {
      setErro('Informe um valor válido maior que zero.');
      return;
    }
    if (!descricao.trim()) {
      setErro('Informe a descrição ou motivo da saída.');
      return;
    }

    setSalvando(true);
    try {
      await apiRequest('/api/caixa/despesas', {
        method: 'POST',
        body: JSON.stringify({
          valor: valDec.toFixed(2),
          categoria,
          descricao: descricao.trim(),
        }),
      });

      toast(`Saída de ${formatMoney(valDec)} registada com sucesso.`);
      setValor('');
      setDescricao('');
      onDespesaRegistada();
      await carregarDespesas();
    } catch (err: unknown) {
      setErro(err instanceof Error ? err.message : 'Falha ao registar saída de caixa.');
    } finally {
      setSalvando(false);
    }
  };

  const totalDespesas = despesas.reduce((acc, d) => acc + Number(d.valor || 0), 0);

  return (
    <Modal title="Saída de Caixa / Despesa da Sessão" onClose={onClose} wide>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px, 1fr) minmax(280px, 1.2fr)', gap: 20 }}>
        {/* Formulário */}
        <div>
          <h4 style={{ margin: '0 0 12px 0', fontSize: 15, display: 'flex', alignItems: 'center', gap: 6 }}>
            <ArrowDownRight size={18} color="#ea580c" /> Nova Saída de Gaveta
          </h4>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
            O montante será abatido do dinheiro esperado ao fechar o caixa.
          </p>

          {erro && <Notice kind="error">{erro}</Notice>}

          <form onSubmit={handleSubmit}>
            <Field id="despesa-valor" label="Valor da Saída (MT) *">
              <div style={{ position: 'relative' }}>
                <input
                  id="despesa-valor"
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  placeholder="Ex: 200.00"
                  value={valor}
                  onChange={(e) => setValor(e.target.value)}
                  style={{ fontSize: 16, fontWeight: 600, paddingLeft: 36 }}
                />
                <DollarSign
                  size={16}
                  color="var(--text-muted)"
                  style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </Field>

            <Field id="despesa-categoria" label="Categoria">
              <div style={{ position: 'relative' }}>
                <select
                  id="despesa-categoria"
                  value={categoria}
                  onChange={(e) => setCategoria(e.target.value)}
                  style={{ paddingLeft: 36 }}
                >
                  {CATEGORIAS.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label}
                    </option>
                  ))}
                </select>
                <Tag
                  size={16}
                  color="var(--text-muted)"
                  style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </Field>

            <Field id="despesa-descricao" label="Descrição / Justificação *">
              <div style={{ position: 'relative' }}>
                <input
                  id="despesa-descricao"
                  type="text"
                  required
                  placeholder="Ex: Recarga Credelec, frete do pão, almoço..."
                  value={descricao}
                  onChange={(e) => setDescricao(e.target.value)}
                  style={{ paddingLeft: 36 }}
                />
                <FileText
                  size={16}
                  color="var(--text-muted)"
                  style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                />
              </div>
            </Field>

            <div style={{ marginTop: 20, display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button type="button" className="btn-secondary" onClick={onClose} disabled={salvando}>
                Fechar
              </button>
              <button type="submit" className="btn-primary" disabled={salvando}>
                {salvando ? <Spinner size="small" /> : 'Registar Saída'}
              </button>
            </div>
          </form>
        </div>

        {/* Lista de saídas registadas */}
        <div style={{ borderLeft: '1px solid var(--border-color)', paddingLeft: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h4 style={{ margin: 0, fontSize: 15, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Clock size={16} color="var(--text-secondary)" /> Saídas Desta Sessão
            </h4>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#ea580c' }}>
              Total: -{formatMoney(totalDespesas)}
            </span>
          </div>

          {carregandoLista ? (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <Spinner size="normal" />
            </div>
          ) : despesas.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              Nenhuma saída registada nesta sessão de caixa.
            </div>
          ) : (
            <div style={{ maxHeight: 320, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {despesas.map((d) => (
                <div
                  key={d.id}
                  style={{
                    padding: '10px 12px',
                    borderRadius: 6,
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-color)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span
                        style={{
                          fontSize: 10,
                          fontWeight: 700,
                          padding: '2px 6px',
                          borderRadius: 4,
                          background: 'rgba(234, 88, 12, 0.12)',
                          color: '#ea580c',
                          textTransform: 'uppercase',
                        }}
                      >
                        {d.categoria}
                      </span>
                      <strong style={{ fontSize: 13 }}>{d.descricao}</strong>
                    </div>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {d.criadaEm ? new Date(d.criadaEm).toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit' }) : ''}
                    </span>
                  </div>
                  <span style={{ fontSize: 14, fontWeight: 700, color: '#dc2626' }}>
                    -{formatMoney(d.valor)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
