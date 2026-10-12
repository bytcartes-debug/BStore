import React from 'react';
import { Share, PlusSquare, Check, X } from 'lucide-react';
import './IosInstallBanner.css';

interface IosInstallBannerProps {
  onDismiss: (neverAgain?: boolean) => void;
}

export const IosInstallBanner: React.FC<IosInstallBannerProps> = ({ onDismiss }) => {
  return (
    <aside className="ios-install-banner" aria-label="Instalação do BStore no iOS" role="region">
      <div className="ios-install-header">
        <div className="ios-install-title-group">
          <strong className="ios-install-title">Instalar o BStore no ecrã principal</strong>
          <span className="ios-install-badge">Safari iOS</span>
        </div>
        <button
          type="button"
          className="icon-btn ios-install-close-btn"
          onClick={() => onDismiss(false)}
          aria-label="Fechar guia de instalação"
        >
          <X size={18} strokeWidth={2} aria-hidden="true" />
        </button>
      </div>

      <p className="ios-install-desc">
        Abra a aplicação em ecrã inteiro, com acesso rápido e melhor estabilidade:
      </p>

      <ol className="ios-install-steps">
        <li className="ios-install-step">
          <span className="step-num">1</span>
          <span className="step-text">
            Toque no botão <Share size={15} className="step-inline-icon" aria-hidden="true" />{' '}
            <strong>Partilhar</strong> na barra do Safari.
          </span>
        </li>
        <li className="ios-install-step">
          <span className="step-num">2</span>
          <span className="step-text">
            Percorra a lista e escolha{' '}
            <PlusSquare size={15} className="step-inline-icon" aria-hidden="true" />{' '}
            <strong>Adicionar ao ecrã principal</strong>.
          </span>
        </li>
        <li className="ios-install-step">
          <span className="step-num">3</span>
          <span className="step-text">
            Toque em <Check size={15} className="step-inline-icon" aria-hidden="true" />{' '}
            <strong>Adicionar</strong> no canto superior direito.
          </span>
        </li>
      </ol>

      <div className="ios-install-actions">
        <button
          type="button"
          className="btn-secondary ios-install-btn-dismiss"
          onClick={() => onDismiss(true)}
        >
          Não mostrar mais
        </button>
        <button
          type="button"
          className="btn-primary ios-install-btn-ok"
          onClick={() => onDismiss(false)}
        >
          Entendido
        </button>
      </div>
    </aside>
  );
};
