import { useToast } from '../utils/toast';
import { useState } from 'react';
import { apiRequest } from '../utils/api';
import { useMutation, useResource } from '../utils/useResource';
import type { UserSession } from '../App';
import {
  AlertTriangle,
  Clock,
  Eye,
  EyeOff,
  Infinity as InfinityIcon,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  User,
} from 'lucide-react';
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

interface Usuario {
  id: number;
  nome: string;
  email: string;
  role: string;
  diasAcesso: number | null;
  dataExpiracao: string | null;
  diasRestantes: number;
  expirado: boolean;
}
const days = [
  { label: '14 dias', value: 14 },
  { label: '30 dias', value: 30 },
  { label: 'Permanente', value: 0 },
];
const emptyForm = { nome: '', email: '', password: '', role: 'operator', diasAcesso: 30 };
const loadUsers = (signal: AbortSignal) => apiRequest<Usuario[]>('/api/usuarios', { signal });

function AccessBadge({ user }: { user: Usuario }) {
  if (user.role === 'superuser' || user.diasRestantes === -1)
    return (
      <span className="badge badge-success">
        <InfinityIcon size={13} strokeWidth={2.5} aria-hidden="true" /> Permanente
      </span>
    );
  if (user.expirado)
    return (
      <span className="badge badge-danger">
        <Clock size={13} strokeWidth={2} aria-hidden="true" /> Expirado
      </span>
    );
  return (
    <span className={`badge ${user.diasRestantes <= 7 ? 'badge-warning' : 'badge-info'}`}>
      {user.diasRestantes <= 7 && <AlertTriangle size={12} strokeWidth={2} aria-hidden="true" />}
      {user.diasRestantes} dia(s) restantes
    </span>
  );
}

export default function UsuariosPage({ user }: { user: UserSession }) {
  const { data, loading, error, reload } = useResource(loadUsers);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Usuario | null>(null);
  const [deleting, setDeleting] = useState<Usuario | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [showPassword, setShowPassword] = useState(false);
  const save = useMutation();
  const remove = useMutation();
  const toast = useToast();
  const users = data || [];
  const filtered = users.filter((item) =>
    `${item.nome} ${item.email}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  );
  const openNew = () => {
    setEditing(null);
    setForm(emptyForm);
    setShowPassword(false);
    save.setError(null);
    setShowModal(true);
  };
  const openEdit = (item: Usuario) => {
    setEditing(item);
    setForm({
      nome: item.nome,
      email: item.email,
      password: '',
      role: item.role,
      diasAcesso: item.diasAcesso ?? 0,
    });
    setShowPassword(false);
    save.setError(null);
    setShowModal(true);
  };
  const handleSave = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.nome.trim()) {
      save.setError('Preencha o nome do utilizador.');
      return;
    }
    void save.run(async () => {
      const body = {
        nome: form.nome.trim(),
        email: form.email.trim(),
        role: form.role,
        diasAcesso: form.diasAcesso,
        ...(form.password ? { password: form.password } : {}),
      };
      await apiRequest(editing ? `/api/usuarios/${editing.id}` : '/api/usuarios', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      });
      setShowModal(false);
      toast(editing ? 'Utilizador guardado.' : 'Utilizador criado.');
      void reload();
      if (editing?.id === user.userId && (form.password || form.role !== user.role))
        window.dispatchEvent(new Event('bstore:unauthenticated'));
    });
  };
  const handleDelete = () =>
    void remove.run(async () => {
      if (!deleting) return;
      await apiRequest(`/api/usuarios/${deleting.id}`, { method: 'DELETE' });
      setDeleting(null);
      toast('Utilizador removido.');
      void reload();
    });
  return (
    <div>
      <PageHeading
        title="Utilizadores"
        description="Controle quem entra na loja e durante quanto tempo."
      >
        <button className="btn-primary" onClick={openNew}>
          <Plus size={16} strokeWidth={2.2} aria-hidden="true" /> Novo utilizador
        </button>
      </PageHeading>
      <div className="toolbar">
        <SearchField value={search} onChange={setSearch} label="Pesquisar por nome ou email" />
        <span className="result-count">{filtered.length} utilizador(es)</span>
      </div>
      {error && <LoadError message={error} retry={reload} />}
      {loading && !data ? (
        <Loading />
      ) : (
        data && (
          <div className="card">
            {filtered.length === 0 ? (
              <EmptyState
                title={
                  users.length ? 'Nenhum utilizador encontrado' : 'Nenhum utilizador registado'
                }
                description={
                  users.length
                    ? 'Experimente outro nome ou email.'
                    : 'Crie um utilizador para dar acesso ao sistema.'
                }
                icon={
                  users.length ? (
                    <Search size={36} strokeWidth={1.8} aria-hidden="true" />
                  ) : (
                    <User size={36} strokeWidth={1.8} aria-hidden="true" />
                  )
                }
              >
                {users.length ? (
                  <button className="btn-secondary" onClick={() => setSearch('')}>
                    <RefreshCw size={14} strokeWidth={2} aria-hidden="true" /> Limpar pesquisa
                  </button>
                ) : (
                  <button className="btn-secondary" onClick={openNew}>
                    <Plus size={16} strokeWidth={2} aria-hidden="true" /> Novo utilizador
                  </button>
                )}
              </EmptyState>
            ) : (
              <div className="table-wrapper">
                <table className="responsive-table">
                  <caption className="sr-only">Utilizadores e permissões de acesso</caption>
                  <thead>
                    <tr>
                      <th scope="col">Nome</th>
                      <th scope="col" className="numeric">Email</th>
                      <th scope="col">Perfil</th>
                      <th scope="col">Acesso</th>
                      <th scope="col">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((item) => (
                      <tr key={item.id}>
                        <td className="cell-name" data-label="Nome">
                          {item.nome}
                          {item.id === user.userId && (
                            <span className="self-label"> (a sua conta)</span>
                          )}
                        </td>
                        <td className="cell-secondary" data-label="Email">
                          {item.email}
                        </td>
                        <td data-label="Perfil">
                          <span
                            className={`badge ${item.role === 'superuser' ? 'badge-warning' : 'badge-info'}`}
                          >
                            {item.role === 'superuser' ? (
                              <>
                                <ShieldCheck size={13} strokeWidth={2} aria-hidden="true" /> Administrador
                              </>
                            ) : (
                              <>
                                <User size={13} strokeWidth={2} aria-hidden="true" /> Operador
                              </>
                            )}
                          </span>
                        </td>
                        <td data-label="Acesso">
                          <div className="access-cell">
                            <AccessBadge user={item} />
                            {item.dataExpiracao && <small>Até {item.dataExpiracao}</small>}
                          </div>
                        </td>
                        <td data-label="Ações">
                          <div className="action-group">
                            {item.role !== 'superuser' && (
                              <button
                                className="icon-btn"
                                title="Renovar acesso"
                                aria-label={`Renovar acesso de ${item.nome}`}
                                onClick={() => openEdit(item)}
                              >
                                <RefreshCw size={15} strokeWidth={2} aria-hidden="true" />
                              </button>
                            )}
                            <button
                              className="icon-btn"
                              title="Editar utilizador"
                              aria-label={`Editar ${item.nome}`}
                              onClick={() => openEdit(item)}
                            >
                              <Pencil size={15} strokeWidth={2} aria-hidden="true" />
                            </button>
                            <button
                              className="icon-btn delete"
                              title={
                                item.id === user.userId
                                  ? 'Não pode remover a própria conta'
                                  : 'Remover utilizador'
                              }
                              aria-label={`Remover ${item.nome}`}
                              disabled={item.id === user.userId}
                              onClick={() => {
                                remove.setError(null);
                                setDeleting(item);
                              }}
                            >
                              <Trash2 size={15} strokeWidth={2} aria-hidden="true" />
                            </button>
                          </div>
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
        <Modal
          title={editing ? 'Editar utilizador' : 'Novo utilizador'}
          onClose={() => setShowModal(false)}
          busy={save.pending}
        >
          <form onSubmit={handleSave}>
            <p className="required-note">Os campos com * são obrigatórios.</p>
            {save.error && <Notice>{save.error}</Notice>}
            <fieldset disabled={save.pending}>
              <Field id="user-name" label="Nome *">
                <input
                  id="user-name"
                  value={form.nome}
                  onChange={(e) => setForm({ ...form, nome: e.target.value })}
                  placeholder="Nome completo"
                  required
                />
              </Field>
              <Field id="user-email" label="Email *">
                <input
                  id="user-email"
                  type="email"
                  autoCapitalize="none"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  placeholder="nome@exemplo.com"
                  required
                />
              </Field>
              <Field
                id="user-password"
                label={editing ? 'Nova senha' : 'Senha *'}
                hint={editing ? 'Deixe em branco para manter a senha atual.' : undefined}
              >
                <div className="password-field">
                  <input
                    id="user-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    minLength={4}
                    required={!editing}
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    aria-describedby={editing ? 'user-password-hint' : undefined}
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((value) => !value)}
                  >
                    <span aria-hidden="true">
                      {showPassword ? <EyeOff size={18} strokeWidth={2} /> : <Eye size={18} strokeWidth={2} />}
                    </span>
                  </button>
                </div>
              </Field>
              <Field id="user-role" label="Perfil">
                <select
                  id="user-role"
                  value={form.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value })}
                >
                  <option value="operator">Operador</option>
                  <option value="superuser">Administrador</option>
                </select>
              </Field>
              {form.role !== 'superuser' && (
                <fieldset className="form-group">
                  <legend className="field-label">
                    {editing ? 'Renovar acesso' : 'Tempo de acesso'}
                  </legend>
                  <div className="segmented">
                    {days.map((day) => (
                      <button
                        key={day.value}
                        type="button"
                        aria-pressed={form.diasAcesso === day.value}
                        onClick={() => setForm({ ...form, diasAcesso: day.value })}
                      >
                        {day.label}
                      </button>
                    ))}
                    {!days.some((day) => day.value === form.diasAcesso) && (
                      <button type="button" aria-pressed="true">
                        {form.diasAcesso} dias
                      </button>
                    )}
                  </div>
                  {editing && (
                    <p className="field-hint">
                      {form.diasAcesso > 0
                        ? `Ao guardar, o acesso será renovado por ${form.diasAcesso} dias a partir de hoje.`
                        : 'Ao guardar, o acesso passa a ser permanente.'}
                    </p>
                  )}
                </fieldset>
              )}
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary">
                  {save.pending && <Spinner />}
                  {save.pending
                    ? 'A guardar…'
                    : editing
                      ? 'Guardar alterações'
                      : 'Criar utilizador'}
                </button>
              </div>
            </fieldset>
          </form>
        </Modal>
      )}
      {deleting && (
        <ConfirmDialog
          title="Remover utilizador?"
          onClose={() => setDeleting(null)}
          onConfirm={handleDelete}
          busy={remove.pending}
          error={remove.error}
          label="Remover utilizador"
          danger
        >
          <p>
            O utilizador <strong>{deleting.nome}</strong> deixará de ter acesso ao sistema. Esta
            ação é permanente.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}
