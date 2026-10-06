import { useToast } from '../utils/toast';
import { useState } from 'react';
import { Plus, CheckCircle2, Users } from 'lucide-react';
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
  descricao: string;
  data: string;
}
const loadDebtors = (signal: AbortSignal) => apiRequest<Devedor[]>('/api/devedores', { signal });
const emptyForm = { nome: '', divida: '', descricao: '' };

export default function DevedoresPage() {
  const { data, loading, error, reload } = useResource(loadDebtors);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [selected, setSelected] = useState<Devedor | null>(null);
  const [form, setForm] = useState(emptyForm);
  const save = useMutation();
  const payment = useMutation();
  const toast = useToast();
  const devedores = data || [];
  const filtered = devedores.filter((item) =>
    `${item.nome} ${item.descricao}`
      .toLocaleLowerCase()
      .includes(search.trim().toLocaleLowerCase()),
  );
  const total = devedores.reduce((sum, item) => sum.plus(item.divida), decimal(0));
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
  const confirmPayment = () =>
    void payment.run(async () => {
      if (!selected) return;
      await apiRequest(`/api/devedores/${selected.id}`, { method: 'DELETE' });
      setSelected(null);
      toast('Pagamento confirmado. Dívida removida.');
      void reload();
    });
  return (
    <div>
      <PageHeading
        title="Devedores"
        description="Saiba quem tem valores por pagar e confirme os pagamentos."
      >
        <button className="btn-primary" onClick={openNew}>
          <Plus size={18} /> Registar dívida
        </button>
      </PageHeading>
      <div className="debt-summary">
        <div>
          <span>Total em dívida</span>
          <strong>{data ? formatMoney(total) : '—'}</strong>
        </div>
        <p>
          {devedores.length} devedor{devedores.length === 1 ? '' : 'es'} com pagamento pendente
        </p>
      </div>
      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar devedores" />
        <span className="result-count">{filtered.length} registo(s)</span>
      </div>
      {error && <LoadError message={error} retry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : (
        data && (
          <div className="card">
            {devedores.length === 0 ? (
              <EmptyState
                title="Nenhuma dívida registada"
                description="Quando um cliente ficar a dever, registe aqui o nome e o valor."
                icon={<Users size={28} />}
              >
                <button className="btn-secondary" onClick={openNew}>
                  Registar dívida
                </button>
              </EmptyState>
            ) : filtered.length === 0 ? (
              <EmptyState
                title="Nenhum devedor encontrado"
                description="Experimente outro nome ou limpe a pesquisa."
              >
                <button className="btn-secondary" onClick={() => setSearch('')}>
                  Limpar pesquisa
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
                        Dívida
                      </th>
                      <th scope="col">Descrição</th>
                      <th scope="col">Data</th>
                      <th scope="col">Pagamento</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((item) => (
                      <tr key={item.id}>
                        <td data-label="Nome" className="cell-name">
                          {item.nome}
                        </td>
                        <td data-label="Dívida" className="numeric">
                          <strong>{formatMoney(item.divida)}</strong>
                        </td>
                        <td data-label="Descrição" className="cell-secondary">
                          {item.descricao || 'Sem descrição'}
                        </td>
                        <td data-label="Data" className="cell-secondary">
                          {item.data}
                        </td>
                        <td data-label="Pagamento">
                          <button
                            className="btn-secondary"
                            aria-label={`Confirmar pagamento de ${item.nome}`}
                            onClick={() => {
                              payment.setError(null);
                              setSelected(item);
                            }}
                          >
                            <CheckCircle2 size={17} /> Confirmar pagamento
                          </button>
                        </td>
                      </tr>
                    ))}
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
                  {save.pending && <Spinner />}
                  {save.pending ? 'A guardar…' : 'Registar dívida'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}
      {selected && (
        <ConfirmDialog
          title="Confirmar pagamento?"
          onClose={() => setSelected(null)}
          onConfirm={confirmPayment}
          busy={payment.pending}
          error={payment.error}
          label="Confirmar pagamento"
        >
          <p>
            Confirme que recebeu <strong>{formatMoney(selected.divida)}</strong> de{' '}
            <strong>{selected.nome}</strong>.
          </p>
          <p>O registo desta dívida será removido. Esta ação não pode ser desfeita.</p>
        </ConfirmDialog>
      )}
    </div>
  );
}
