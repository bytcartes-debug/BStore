import { useToast } from '../utils/toast';
import { useState } from 'react';
import { apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import { decimal, formatMoney } from '../utils/decimal';
import {
  PageHeading,
  SearchField,
  Loading,
  LoadError,
  EmptyState,
  Modal,
  Field,
  Notice,
  Spinner,
  ConfirmDialog,
} from '../components/UI';

interface Devedor {
  id: number;
  nome: string;
  divida: string;
  saldo?: string;
  totalPago?: string;
  descricao: string;
  data: string;
  vendaId?: number;
}

interface PagamentoDividaItem {
  id: number;
  valor: string;
  metodo: string;
  criadoEm: string;
  observacao: string;
}

const loadDebtors = (signal: AbortSignal) => apiRequest<Devedor[]>('/api/devedores', { signal });
const emptyForm = { nome: '', divida: '', descricao: '' };

export default function DevedoresPage() {
  const { data, loading, error, reload } = useResource(loadDebtors);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [selectedDevedor, setSelectedDevedor] = useState<Devedor | null>(null);
  const [devedorParaExcluir, setDevedorParaExcluir] = useState<Devedor | null>(null);
  const [form, setForm] = useState(emptyForm);

  // Modal de amortização / pagamento parcial
  const [amortizacaoValor, setAmortizacaoValor] = useState('');
  const [amortizacaoMetodo, setAmortizacaoMetodo] = useState('DINHEIRO');
  const [amortizacaoObs, setAmortizacaoObs] = useState('');

  // Modal de histórico
  const [historicoDevedor, setHistoricoDevedor] = useState<Devedor | null>(null);
  const [historicoPagamentos, setHistoricoPagamentos] = useState<PagamentoDividaItem[]>([]);
  const [historicoCarregando, setHistoricoCarregando] = useState(false);
  const [historicoErro, setHistoricoErro] = useState<string | null>(null);

  const save = useMutation();
  const payment = useMutation();
  const deleteMutation = useMutation();
  const toast = useToast();

  const devedores = data || [];
  const filtered = devedores.filter((item) =>
    `${item.nome} ${item.descricao}`
      .toLocaleLowerCase()
      .includes(search.trim().toLocaleLowerCase()),
  );

  const totalSaldo = devedores.reduce((sum, item) => sum.plus(item.saldo ?? item.divida), decimal(0));
  const totalOriginal = devedores.reduce((sum, item) => sum.plus(item.divida), decimal(0));

  const openNew = () => {
    save.setError(null);
    setShowModal(true);
  };

  const handleSave = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.nome.trim()) {
      save.setError('Preencha o nome do devedor.');
      return;
    }
    void save.run(async () => {
      await apiRequest('/api/devedores', {
        method: 'POST',
        body: JSON.stringify({
          nome: form.nome.trim(),
          divida: form.divida,
          descricao: form.descricao.trim(),
        }),
      });
      setShowModal(false);
      setForm(emptyForm);
      toast('Dívida registada.');
      void reload();
    });
  };

  const abrirAmortizacao = (item: Devedor) => {
    setSelectedDevedor(item);
    setAmortizacaoValor(item.saldo ?? item.divida);
    setAmortizacaoMetodo('DINHEIRO');
    setAmortizacaoObs('');
    payment.setError(null);
  };

  const confirmarAmortizacao = (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedDevedor) return;
    const saldoAtual = decimal(selectedDevedor.saldo ?? selectedDevedor.divida);
    const valorNum = decimal(amortizacaoValor || '0');
    if (valorNum.lte(0)) {
      payment.setError('O valor do pagamento deve ser maior que zero.');
      return;
    }
    if (valorNum.gt(saldoAtual)) {
      payment.setError(`O valor não pode ser superior ao saldo devedor (${formatMoney(saldoAtual)}).`);
      return;
    }

    void payment.run(async () => {
      await apiRequest(`/api/devedores/${selectedDevedor.id}/pagamentos`, {
        method: 'POST',
        body: JSON.stringify({
          valor: amortizacaoValor,
          metodo: amortizacaoMetodo,
          observacao: amortizacaoObs.trim(),
        }),
      });
      setSelectedDevedor(null);
      toast(`Pagamento de ${formatMoney(amortizacaoValor)} registado.`);
      void reload();
    });
  };

  const abrirHistorico = async (item: Devedor) => {
    setHistoricoDevedor(item);
    setHistoricoCarregando(true);
    setHistoricoErro(null);
    try {
      const res = await apiRequest<PagamentoDividaItem[]>(`/api/devedores/${item.id}/pagamentos`);
      setHistoricoPagamentos(res);
    } catch (e: unknown) {
      setHistoricoErro(e instanceof Error ? e.message : 'Falha ao carregar histórico de pagamentos.');
    } finally {
      setHistoricoCarregando(false);
    }
  };

  const confirmarExclusao = () =>
    void deleteMutation.run(async () => {
      if (!devedorParaExcluir) return;
      await apiRequest(`/api/devedores/${devedorParaExcluir.id}`, { method: 'DELETE' });
      setDevedorParaExcluir(null);
      toast('Registo removido com sucesso.');
      void reload();
    });

  return (
    <div className="devedores-page">
      <PageHeading
        title="Devedores"
        description="Controle saldos em aberto, amortizações parciais e histórico de pagamentos."
      >
        <button className="btn-primary" onClick={openNew}>
          <span aria-hidden="true">➕</span> Registar dívida
        </button>
      </PageHeading>

      <div className="debt-summary card" style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        <div className="debt-summary-amount">
          <span>Saldo Total em Aberto</span>
          <strong style={{ color: 'var(--color-danger)' }}>{data ? formatMoney(totalSaldo) : '—'}</strong>
        </div>
        <div className="debt-summary-amount">
          <span>Total Original Registado</span>
          <strong style={{ color: 'var(--text-muted, #666)' }}>{data ? formatMoney(totalOriginal) : '—'}</strong>
        </div>
        <p className="debt-summary-count" style={{ alignSelf: 'center', margin: 0 }}>
          👥 {devedores.length} cliente{devedores.length === 1 ? '' : 's'} com conta corrente
        </p>
      </div>

      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar por nome ou descrição" />
        <span className="result-count">{filtered.length} registo(s)</span>
      </div>

      {error && <LoadError message={error} retry={reload} />}

      {loading && !data ? (
        <Loading label="A carregar a lista de devedores…" />
      ) : (
        data && (
          <div className="card">
            {devedores.length === 0 ? (
              <EmptyState
                title="Nenhuma dívida registada"
                description="Quando um cliente levar produtos a fiado, o registo aparecerá aqui."
                icon="👥"
              >
                <button className="btn-secondary" onClick={openNew}>
                  ➕ Registar dívida
                </button>
              </EmptyState>
            ) : filtered.length === 0 ? (
              <EmptyState
                title="Nenhum devedor encontrado"
                description="Experimente outro nome ou limpe a pesquisa."
                icon="🔍"
              >
                <button className="btn-secondary" onClick={() => setSearch('')}>
                  🔄 Limpar pesquisa
                </button>
              </EmptyState>
            ) : (
              <div className="table-wrapper">
                <table className="responsive-table">
                  <caption className="sr-only">Dívidas pendentes</caption>
                  <thead>
                    <tr>
                      <th scope="col">Nome</th>
                      <th scope="col" className="numeric">
                        Saldo Devedor
                      </th>
                      <th scope="col" className="numeric">
                        Total Pago
                      </th>
                      <th scope="col" className="numeric">
                        Dívida Inicial
                      </th>
                      <th scope="col">Data</th>
                      <th scope="col">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((item) => {
                      const saldo = decimal(item.saldo ?? item.divida);
                      const isQuitado = saldo.lte(0);
                      return (
                        <tr key={item.id}>
                          <td data-label="Nome" className="cell-name">
                            <strong>{item.nome}</strong>
                            {item.descricao && (
                              <div style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
                                {item.descricao}
                              </div>
                            )}
                          </td>
                          <td data-label="Saldo Devedor" className="numeric font-mono">
                            <strong style={{ color: isQuitado ? 'var(--color-brand)' : 'var(--color-danger)' }}>
                              {formatMoney(saldo)}
                            </strong>
                            {isQuitado && <span style={{ marginLeft: 6, fontSize: 12, color: 'var(--color-brand)' }}>✓ Pago</span>}
                          </td>
                          <td data-label="Total Pago" className="numeric font-mono cell-secondary">
                            {formatMoney(item.totalPago ?? '0.00')}
                          </td>
                          <td data-label="Dívida Inicial" className="numeric font-mono cell-secondary">
                            {formatMoney(item.divida)}
                          </td>
                          <td data-label="Data" className="cell-secondary">
                            {item.data}
                          </td>
                          <td data-label="Ações">
                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                              <button
                                className="btn-secondary"
                                aria-label={`Confirmar pagamento de ${item.nome}`}
                                onClick={() => {
                                  deleteMutation.setError(null);
                                  setDevedorParaExcluir(item);
                                }}
                              >
                                <span aria-hidden="true">✅</span> Confirmar pagamento
                              </button>
                              {!isQuitado && (
                                <button
                                  type="button"
                                  className="btn-primary"
                                  style={{ padding: '4px 8px', fontSize: 13 }}
                                  onClick={() => abrirAmortizacao(item)}
                                >
                                  💳 Amortizar
                                </button>
                              )}
                              <button
                                type="button"
                                className="btn-secondary"
                                style={{ padding: '4px 8px', fontSize: 13 }}
                                onClick={() => void abrirHistorico(item)}
                              >
                                📋 Histórico
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )
      )}

      {showModal && (
        <Modal title="Registar dívida" onClose={() => setShowModal(false)} busy={save.pending}>
          <form onSubmit={handleSave}>
            <p className="required-note">Os campos com * são obrigatórios.</p>
            {save.error && <Notice>{save.error}</Notice>}
            <fieldset disabled={save.pending}>
              <Field id="debtor-name" label="Nome do devedor *">
                <input
                  id="debtor-name"
                  value={form.nome}
                  onChange={(e) => setForm({ ...form, nome: e.target.value })}
                  required
                  autoComplete="off"
                  placeholder="Nome do cliente"
                />
              </Field>
              <Field id="debtor-amount" label="Valor da dívida (MT) *">
                <input
                  id="debtor-amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  inputMode="decimal"
                  value={form.divida}
                  onChange={(e) => setForm({ ...form, divida: e.target.value })}
                  required
                  placeholder="0,00"
                />
              </Field>
              <Field id="debtor-description" label="Descrição">
                <textarea
                  id="debtor-description"
                  value={form.descricao}
                  onChange={(e) => setForm({ ...form, descricao: e.target.value })}
                  placeholder="Produtos em dívida ou informações para identificar o cliente"
                />
              </Field>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary">
                  {save.pending && <Spinner size="small" />}
                  {save.pending ? ' A guardar…' : '💾 Registar dívida'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {selectedDevedor && (
        <Modal
          title={`Registar Pagamento — ${selectedDevedor.nome}`}
          onClose={() => setSelectedDevedor(null)}
          busy={payment.pending}
        >
          <form onSubmit={confirmarAmortizacao}>
            {payment.error && <Notice>{payment.error}</Notice>}
            <div style={{ marginBottom: 16, padding: 12, background: 'var(--bg-subtle)', borderRadius: 8 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <span>Saldo em dívida:</span>
                <strong style={{ color: 'var(--color-danger)' }}>
                  {formatMoney(selectedDevedor.saldo ?? selectedDevedor.divida)}
                </strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--text-muted, #666)' }}>
                <span>Total já amortizado:</span>
                <span>{formatMoney(selectedDevedor.totalPago ?? '0.00')}</span>
              </div>
            </div>

            <fieldset disabled={payment.pending}>
              <Field id="payment-amount" label="Valor a pagar (MT) *">
                <input
                  id="payment-amount"
                  type="number"
                  min="0.01"
                  max={selectedDevedor.saldo ?? selectedDevedor.divida}
                  step="0.01"
                  inputMode="decimal"
                  value={amortizacaoValor}
                  onChange={(e) => setAmortizacaoValor(e.target.value)}
                  required
                  placeholder="0,00"
                />
              </Field>

              <Field id="payment-method" label="Forma de pagamento *">
                <select
                  id="payment-method"
                  value={amortizacaoMetodo}
                  onChange={(e) => setAmortizacaoMetodo(e.target.value)}
                >
                  <option value="DINHEIRO">Dinheiro</option>
                  <option value="MPESA">M-Pesa</option>
                  <option value="EMOLA">e-Mola</option>
                  <option value="MKESH">mKesh</option>
                  <option value="CARTAO">Cartão</option>
                </select>
              </Field>

              <Field id="payment-obs" label="Observação">
                <input
                  id="payment-obs"
                  value={amortizacaoObs}
                  onChange={(e) => setAmortizacaoObs(e.target.value)}
                  placeholder="Ex: Pagamento da primeira parcela"
                />
              </Field>

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setSelectedDevedor(null)}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary">
                  {payment.pending ? <Spinner size="small" /> : '✅ Confirmar Pagamento'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}

      {historicoDevedor && (
        <Modal
          title={`Histórico de Pagamentos — ${historicoDevedor.nome}`}
          onClose={() => setHistoricoDevedor(null)}
          wide
        >
          {historicoErro && <Notice>{historicoErro}</Notice>}
          {historicoCarregando ? (
            <div style={{ padding: 24, textAlign: 'center' }}>
              <Spinner />
              <p>A carregar amortizações…</p>
            </div>
          ) : historicoPagamentos.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted, #666)' }}>
              Nenhum pagamento registado para este devedor até o momento.
            </div>
          ) : (
            <table className="responsive-table">
              <thead>
                <tr>
                  <th>Data / Hora</th>
                  <th>Método</th>
                  <th className="numeric">Valor Pago</th>
                  <th>Observação</th>
                </tr>
              </thead>
              <tbody>
                {historicoPagamentos.map((p) => (
                  <tr key={p.id}>
                    <td>{p.criadoEm.replace('T', ' ').slice(0, 16)}</td>
                    <td>
                      <span className="badge">{p.metodo}</span>
                    </td>
                    <td className="numeric font-mono" style={{ color: 'var(--color-brand)', fontWeight: 600 }}>
                      {formatMoney(p.valor)}
                    </td>
                    <td className="cell-secondary">{p.observacao || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="modal-actions" style={{ marginTop: 16 }}>
            <button type="button" className="btn-secondary" onClick={() => setHistoricoDevedor(null)}>
              Fechar
            </button>
          </div>
        </Modal>
      )}

      {devedorParaExcluir && (
        <ConfirmDialog
          title="Confirmar pagamento?"
          onClose={() => setDevedorParaExcluir(null)}
          onConfirm={confirmarExclusao}
          busy={deleteMutation.pending}
          error={deleteMutation.error}
          label="Confirmar pagamento"
        >
          <p>
            Confirme que recebeu <strong>{formatMoney(devedorParaExcluir.divida)}</strong> de{' '}
            <strong>{devedorParaExcluir.nome}</strong>.
          </p>
          <p>O registo desta dívida será removido. Esta ação não pode ser desfeita.</p>
        </ConfirmDialog>
      )}
    </div>
  );
}
