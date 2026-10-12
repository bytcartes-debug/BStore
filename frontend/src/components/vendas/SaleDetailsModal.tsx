import React, { useState } from 'react';
import { Ban, CheckCircle2, Printer, RotateCcw, Share2, XCircle } from 'lucide-react';
import { Modal } from '../UI';
import { formatMoney, formatQuantity } from '../../utils/decimal';
import { formatarMetodoPagamento, gerarTextoRecibo, type ReciboDados } from '../../utils/recibo';
import { formatarDataHora } from '../../utils/dataHora';
import type { Devedor, VendaDocumento } from './types';

interface SaleDetailsModalProps {
  vendaDetalhes: VendaDocumento;
  devedores: Devedor[];
  onClose: () => void;
  onImprimirRecibo: (dados: ReciboDados) => void;
  onIniciarDevolucao: (venda: VendaDocumento) => void;
  onAnular: (venda: VendaDocumento) => void;
}

export const SaleDetailsModal: React.FC<SaleDetailsModalProps> = ({
  vendaDetalhes,
  devedores,
  onClose,
  onImprimirRecibo,
  onIniciarDevolucao,
  onAnular,
}) => {
  const clienteObj = devedores.find((d) => d.id === vendaDetalhes.clienteId);
  const [partilhado, setPartilhado] = useState(false);

  const montarDadosRecibo = (): ReciboDados => ({
    id: vendaDetalhes.id,
    numero: vendaDetalhes.numero,
    data: vendaDetalhes.data,
    hora: vendaDetalhes.hora,
    total: vendaDetalhes.total,
    troco: vendaDetalhes.troco,
    observacao: vendaDetalhes.observacao,
    clienteNome: clienteObj?.nome,
    itens:
      vendaDetalhes.itens && vendaDetalhes.itens.length > 0
        ? vendaDetalhes.itens.map((it) => ({
            produto: it.produto,
            quantidade: it.quantidade,
            precoUnitario: it.precoUnitario,
            total: it.total,
          }))
        : [
            {
              produto: vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`,
              quantidade: vendaDetalhes.quantidade || '1',
              precoUnitario: vendaDetalhes.total,
              total: vendaDetalhes.total,
            },
          ],
    pagamentos: vendaDetalhes.pagamentos?.map((p) => ({
      metodo: p.metodo,
      valor: p.valor,
      troco: p.troco,
    })),
  });

  const handlePrint = () => {
    onImprimirRecibo(montarDadosRecibo());
  };

  const handleShare = async () => {
    const dados = montarDadosRecibo();
    const texto = gerarTextoRecibo(dados, 'BStore');

    if (typeof navigator !== 'undefined' && 'share' in navigator) {
      try {
        await navigator.share({
          title: `Recibo BStore #${vendaDetalhes.numero}`,
          text: texto,
        });
        setPartilhado(true);
        setTimeout(() => setPartilhado(false), 2500);
        return;
      } catch {
        // Usuário cancelou ou navegador não suportou, segue para o fallback
      }
    }

    // Fallback: abrir WhatsApp com texto codificado
    const url = `https://wa.me/?text=${encodeURIComponent(texto)}`;
    window.open(url, '_blank');
    setPartilhado(true);
    setTimeout(() => setPartilhado(false), 2500);
  };

  return (
    <Modal
      title={`Detalhes do Documento — Venda #${vendaDetalhes.numero}`}
      onClose={onClose}
      wide
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 12,
          marginBottom: 16,
        }}
      >
        <div>
          <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
            Data e Hora:
          </span>
          <strong style={{ display: 'block' }}>
            {formatarDataHora(vendaDetalhes.criadaEm || `${vendaDetalhes.data}T${vendaDetalhes.hora || '00:00'}`)}
          </strong>
        </div>
        <div>
          <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Estado:</span>
          <div>
            {vendaDetalhes.estado === 'ANULADA' ? (
              <span className="badge" style={{ background: '#fee2e2', color: '#991b1b' }}>
                <XCircle size={13} strokeWidth={2} aria-hidden="true" /> ANULADA
              </span>
            ) : vendaDetalhes.estado === 'DEVOLVIDA' ||
              vendaDetalhes.estado === 'PARCIALMENTE_DEVOLVIDA' ? (
              <span className="badge" style={{ background: '#fef3c7', color: '#92400e' }}>
                <RotateCcw size={13} strokeWidth={2} aria-hidden="true" />{' '}
                {vendaDetalhes.estado}
              </span>
            ) : (
              <span className="badge" style={{ background: '#dcfce7', color: '#166534' }}>
                <CheckCircle2 size={13} strokeWidth={2} aria-hidden="true" /> CONCLUÍDA
              </span>
            )}
          </div>
        </div>
        <div>
          <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
            Total da Venda:
          </span>
          <strong
            style={{
              display: 'block',
              fontSize: 18,
              color: 'var(--color-brand)',
            }}
          >
            {formatMoney(vendaDetalhes.total)}
          </strong>
        </div>
        {vendaDetalhes.lucro && (
          <div>
            <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>
              Lucro Bruto:
            </span>
            <strong style={{ display: 'block', color: '#16a34a' }}>
              {formatMoney(vendaDetalhes.lucro)}
            </strong>
          </div>
        )}
      </div>

      {vendaDetalhes.motivoAnulacao && (
        <div
          style={{
            padding: 12,
            background: '#fee2e2',
            borderRadius: 8,
            marginBottom: 16,
            color: '#991b1b',
          }}
        >
          <strong>Motivo da Anulação:</strong> {vendaDetalhes.motivoAnulacao}
          {vendaDetalhes.anuladaEm && (
            <div style={{ fontSize: 12, marginTop: 4 }}>
              Em: {vendaDetalhes.anuladaEm.replace('T', ' ').slice(0, 16)}
            </div>
          )}
        </div>
      )}

      {vendaDetalhes.observacao && (
        <p style={{ fontSize: 13, fontStyle: 'italic', marginBottom: 16 }}>
          Observação: {vendaDetalhes.observacao}
        </p>
      )}

      <h4 style={{ margin: '16px 0 8px 0' }}>Itens da Venda</h4>
      <table className="responsive-table" style={{ marginBottom: 16 }}>
        <thead>
          <tr>
            <th scope="col">Produto</th>
            <th scope="col" className="numeric">Quantidade</th>
            <th scope="col" className="numeric">Preço Unitário</th>
            <th scope="col" className="numeric">Total</th>
          </tr>
        </thead>
        <tbody>
          {vendaDetalhes.itens && vendaDetalhes.itens.length > 0 ? (
            vendaDetalhes.itens.map((it) => (
              <tr key={it.id}>
                <td data-label="Produto">
                  <strong>{it.produto}</strong>
                  {it.notaDesconto && (
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                      Desc: {it.notaDesconto}
                    </div>
                  )}
                </td>
                <td data-label="Quantidade" className="numeric font-mono">
                  {formatQuantity(it.quantidade, '')}
                </td>
                <td data-label="Preço Unitário" className="numeric font-mono">
                  {formatMoney(it.precoUnitario)}
                </td>
                <td data-label="Total" className="numeric font-mono" style={{ fontWeight: 600 }}>
                  {formatMoney(it.total)}
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td data-label="Produto">{vendaDetalhes.produto || `Venda #${vendaDetalhes.numero}`}</td>
              <td data-label="Quantidade" className="numeric font-mono">
                {formatQuantity(vendaDetalhes.quantidade || '1', '')}
              </td>
              <td data-label="Preço Unitário" className="numeric font-mono">{formatMoney(vendaDetalhes.total)}</td>
              <td data-label="Total" className="numeric font-mono" style={{ fontWeight: 600 }}>
                {formatMoney(vendaDetalhes.total)}
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {vendaDetalhes.pagamentos && vendaDetalhes.pagamentos.length > 0 && (
        <>
          <h4 style={{ margin: '16px 0 8px 0' }}>Formas de Pagamento</h4>
          <table className="responsive-table">
            <thead>
              <tr>
                <th scope="col">Método</th>
                <th scope="col" className="numeric">Valor</th>
                <th scope="col" className="numeric">Troco</th>
                <th scope="col">Referência</th>
              </tr>
            </thead>
            <tbody>
              {vendaDetalhes.pagamentos.map((p) => (
                <tr key={p.id}>
                  <td data-label="Método">
                    <strong>{formatarMetodoPagamento(p.metodo)}</strong>
                  </td>
                  <td data-label="Valor" className="numeric font-mono">{formatMoney(p.valor)}</td>
                  <td data-label="Troco" className="numeric font-mono">{formatMoney(p.troco || '0.00')}</td>
                  <td data-label="Referência" style={{ fontSize: 12 }}>{p.referencia || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <div className="modal-actions" style={{ marginTop: 20, flexWrap: 'wrap', gap: 8 }}>
        <button
          type="button"
          className="btn-secondary"
          onClick={onClose}
        >
          Fechar
        </button>
        <button
          type="button"
          className="btn-secondary"
          onClick={handlePrint}
        >
          <Printer size={15} strokeWidth={2} aria-hidden="true" /> Imprimir Recibo
        </button>
        <button
          type="button"
          className="btn-secondary"
          onClick={handleShare}
          title="Partilhar recibo por WhatsApp ou mensagem"
        >
          <Share2 size={15} strokeWidth={2} aria-hidden="true" />
          {partilhado ? 'Recibo enviado!' : 'Partilhar Recibo'}
        </button>

        {vendaDetalhes.estado !== 'ANULADA' &&
          vendaDetalhes.estado !== 'DEVOLVIDA' && (
            <button
              type="button"
              className="btn-secondary"
              onClick={() => onIniciarDevolucao(vendaDetalhes)}
            >
              <RotateCcw size={15} strokeWidth={2} aria-hidden="true" /> Devolver produtos
            </button>
          )}

        {vendaDetalhes.estado === 'CONCLUIDA' && (
          <button
            type="button"
            className="btn-secondary"
            style={{ color: '#dc2626' }}
            onClick={() => onAnular(vendaDetalhes)}
          >
            <Ban size={15} strokeWidth={2} aria-hidden="true" /> Anular
          </button>
        )}
      </div>
    </Modal>
  );
};
