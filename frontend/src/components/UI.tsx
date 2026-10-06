import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ToastContext } from '../utils/toast';
import { useDialog } from '../utils/useDialog';
import { AlertCircle, CheckCircle2, Inbox, RefreshCw, Search, X } from 'lucide-react';

export function Spinner() {
  return <span className="spinner" aria-hidden="true" />;
}

export function Loading({
  fullScreen = false,
  label = 'A carregar os dados…',
}: {
  fullScreen?: boolean;
  label?: string;
}) {
  return (
    <div
      className={fullScreen ? 'loading-screen' : 'loading-state'}
      role="status"
      aria-live="polite"
    >
      <Spinner />
      <p>{label}</p>
    </div>
  );
}

export function Notice({
  children,
  kind = 'error',
}: {
  children: ReactNode;
  kind?: 'error' | 'success' | 'info';
}) {
  return (
    <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {kind === 'success' ? (
        <CheckCircle2 size={18} aria-hidden="true" />
      ) : (
        <AlertCircle size={18} aria-hidden="true" />
      )}
      <div>{children}</div>
    </div>
  );
}

export function LoadError({ message, retry }: { message: string; retry: () => void }) {
  return (
    <div className="load-error">
      <Notice>{message}</Notice>
      <button className="btn-secondary" onClick={retry}>
        <RefreshCw size={16} /> Tentar novamente
      </button>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  children,
  icon = <Inbox size={28} />,
}: {
  title: string;
  description: string;
  children?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon" aria-hidden="true">
        {icon}
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
      {children}
    </div>
  );
}

export function PageHeading({
  title,
  description,
  children,
}: {
  title: string;
  description: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}

export function SearchField({
  value,
  onChange,
  label = 'Pesquisar',
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  label?: string;
  placeholder?: string;
}) {
  const id = useId();
  return (
    <div className="search-field">
      <label className="sr-only" htmlFor={id}>
        {label}
      </label>
      <Search size={18} aria-hidden="true" />
      <input
        id={id}
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || label}
      />
    </div>
  );
}

export function Field({
  id,
  label,
  children,
  hint,
}: {
  id: string;
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  const [error, setError] = useState('');
  return (
    <div
      className="form-group"
      onInvalid={(event) => {
        const input = event.target as HTMLInputElement;
        setError(
          input.validity.valueMissing
            ? 'Preencha este campo.'
            : input.validity.typeMismatch
              ? 'Introduza um valor válido.'
              : input.validationMessage,
        );
        input.setAttribute('aria-invalid', 'true');
        input.setAttribute('aria-describedby', `${id}-error`);
      }}
      onInput={(event) => {
        setError('');
        const input = event.target as HTMLInputElement;
        input.removeAttribute('aria-invalid');
        if (hint) input.setAttribute('aria-describedby', `${id}-hint`);
        else input.removeAttribute('aria-describedby');
      }}
    >
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && (
        <p id={`${id}-hint`} className="field-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}

export function Modal({
  title,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useDialog(ref);
  return (
    <dialog
      ref={ref}
      className={`modal-container${wide ? ' modal-wide' : ''}`}
      aria-labelledby={titleId}
      aria-busy={busy}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="modal-header">
        <h3 id={titleId}>{title}</h3>
        <button
          type="button"
          className="icon-btn"
          aria-label="Fechar janela"
          onClick={onClose}
          disabled={busy}
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}

export function ConfirmDialog({
  title,
  children,
  onClose,
  onConfirm,
  busy,
  error,
  label = 'Confirmar',
  danger = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  onConfirm: () => void;
  busy: boolean;
  error?: string | null;
  label?: string;
  danger?: boolean;
}) {
  return (
    <Modal title={title} onClose={onClose} busy={busy}>
      <div className="confirm-content">{children}</div>
      {error && <Notice>{error}</Notice>}
      <div className="modal-actions">
        <button className="btn-secondary" onClick={onClose} disabled={busy}>
          Cancelar
        </button>
        <button
          className={danger ? 'btn-danger' : 'btn-primary'}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy && <Spinner />}
          {busy ? 'A processar…' : label}
        </button>
      </div>
    </Modal>
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; id: number } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(timer);
  }, [toast]);
  return (
    <ToastContext.Provider value={(message) => setToast({ message, id: Date.now() })}>
      {children}
      <div className="toast-region" aria-live="polite" aria-atomic="true">
        {toast && (
          <div className="toast">
            <CheckCircle2 size={20} aria-hidden="true" />
            <p>{toast.message}</p>
            <button
              className="icon-btn"
              aria-label="Fechar notificação"
              onClick={() => setToast(null)}
            >
              <X size={18} />
            </button>
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}
