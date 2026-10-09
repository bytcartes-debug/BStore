import { useState, useEffect, useCallback } from 'react';
import { apiRequest } from '../utils/api';
import { formatMoney } from '../utils/decimal';
import { Modal, Spinner, Notice } from './UI';

interface FechoCaixaData {
  data: string;
  totalVendido: string;
  totalCusto: string;
  lucroEstimado: string;
  numeroVendas: number;
  vendasAnuladas: number;
  totalFiado: string;
  totaisPorMetodo: Record<string, string>;
}

interface FechoCaixaModalProps {
  onClose: () => void;
}

export function FechoCaixaModal({ onClose }: FechoCaixaModalProps) {
  const [dataSelecionada, setDataSelecionada] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [dados, setDados] = useState<FechoCaixaData | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregarFecho = useCallback(async (data: string) => {
    setCarregando(true);
    setErro(null);
    try {
      const res = await apiRequest<FechoCaixaData>(`/api/caixa/fecho?data=${data}`);
      setDados(res);
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Falha ao carregar fecho de caixa.');
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregarFecho(dataSelecionada);
  }, [dataSelecionada, carregarFecho]);

  const imprimirFecho = () => {
    if (!dados) return;
    const win = window.open('', '_blank', 'width=450,height=600');
    if (!win) return;
    const metodosHtml = Object.entries(dados.totaisPorMetodo || {})
      .map(([m, val]) => `<tr><td style="padding:4px 0">${m}:</td><td style="text-align:right;font-weight:bold">${formatMoney(val)}</td></tr>`)
      .join('');

    win.document.write(`
      <!DOCTYPE html>
      <html>
        <head>
          <title>Fecho de Caixa - ${dados.data}</title>
          <style>
            body { font-family: monospace; font-size: 13px; padding: 20px; line-height: 1.5; color: #111; }
            h2, h3 { margin: 0 0 8px 0; text-align: center; }
            hr { border: none; border-top: 1px dashed #666; margin: 12px 0; }
            table { width: 100%; border-collapse: collapse; }
            .total-row { font-size: 15px; font-weight: bold; border-top: 1px solid #000; }
          </style>
        </head>
        <body>
          <h2>FLEX STOCK</h2>
          <h3>FECHO DIÁRIO DE CAIXA</h3>
          <p style="text-align:center">Data: ${dados.data}</p>
          <hr />
          <table>
            <tr><td>Total Vendido:</td><td style="text-align:right;font-weight:bold">${formatMoney(dados.totalVendido)}</td></tr>
            <tr><td>Total Custo:</td><td style="text-align:right">${formatMoney(dados.totalCusto)}</td></tr>
            <tr><td>Lucro Estimado:</td><td style="text-align:right;color:#0a7a3b;font-weight:bold">${formatMoney(dados.lucroEstimado)}</td></tr>
            <tr><td>Vendas Concluídas:</td><td style="text-align:right">${dados.numeroVendas}</td></tr>
            <tr><td>Vendas Anuladas:</td><td style="text-align:right;color:#c00">${dados.vendasAnuladas}</td></tr>
            <tr><td>Vendas a Fiado:</td><td style="text-align:right">${formatMoney(dados.totalFiado)}</td></tr>
          </table>
          <hr />
          <h4 style="margin:8px 0">RECEBIMENTOS POR MÉTODO</h4>
          <table>
            ${metodosHtml}
          </table>
          <hr />
          <p style="text-align:center;font-size:11px">Emitido em ${new Date().toLocaleString('pt-MZ')}</p>
        </body>
      </html>
    `);
    win.document.close();
    win.focus();
    setTimeout(() => {
      win.print();
      win.close();
    }, 300);
  };

  return (
    <Modal title="📊 Fecho de Caixa Diário" onClose={onClose} wide>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
        <label htmlFor="fecho-data" style={{ fontWeight: 600 }}>
          Data do Caixa:
        </label>
        <input
          id="fecho-data"
          type="date"
          value={dataSelecionada}
          onChange={(e) => setDataSelecionada(e.target.value)}
          style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #ccc)' }}
        />
        <button
          type="button"
          className="btn-secondary"
          onClick={() => void carregarFecho(dataSelecionada)}
          disabled={carregando}
        >
          {carregando ? <Spinner size="small" /> : '🔄 Atualizar'}
        </button>
      </div>

      {erro && <Notice>{erro}</Notice>}

      {carregando && !dados ? (
        <div style={{ padding: 32, textAlign: 'center' }}>
          <Spinner />
          <p style={{ marginTop: 8 }}>A carregar totais do caixa…</p>
        </div>
      ) : dados ? (
        <div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: 12,
              marginBottom: 20,
            }}
          >
            <div className="card" style={{ padding: 12, textAlign: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Total Vendido</span>
              <strong style={{ display: 'block', fontSize: 18, color: 'var(--primary, #1e40af)' }}>
                {formatMoney(dados.totalVendido)}
              </strong>
            </div>

            <div className="card" style={{ padding: 12, textAlign: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Custo Mercadorias</span>
              <strong style={{ display: 'block', fontSize: 18 }}>
                {formatMoney(dados.totalCusto)}
              </strong>
            </div>

            <div className="card" style={{ padding: 12, textAlign: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Lucro Estimado</span>
              <strong style={{ display: 'block', fontSize: 18, color: '#16a34a' }}>
                {formatMoney(dados.lucroEstimado)}
              </strong>
            </div>

            <div className="card" style={{ padding: 12, textAlign: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Vendas / Anuladas</span>
              <strong style={{ display: 'block', fontSize: 18 }}>
                {dados.numeroVendas} / <span style={{ color: '#dc2626' }}>{dados.vendasAnuladas}</span>
              </strong>
            </div>

            <div className="card" style={{ padding: 12, textAlign: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Vendido a Fiado</span>
              <strong style={{ display: 'block', fontSize: 18, color: '#d97706' }}>
                {formatMoney(dados.totalFiado)}
              </strong>
            </div>
          </div>

          <div className="card" style={{ padding: 16 }}>
            <h4 style={{ margin: '0 0 12px 0' }}>💳 Totais por Forma de Pagamento</h4>
            <table className="responsive-table">
              <thead>
                <tr>
                  <th>Método</th>
                  <th className="numeric">Total Recebido</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(dados.totaisPorMetodo || {}).map(([metodo, valor]) => (
                  <tr key={metodo}>
                    <td>
                      <strong>{metodo}</strong>
                    </td>
                    <td className="numeric font-mono" style={{ fontWeight: 600 }}>
                      {formatMoney(valor)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="modal-actions" style={{ marginTop: 20 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              Fechar
            </button>
            <button type="button" className="btn-primary" onClick={imprimirFecho}>
              🖨️ Imprimir Fecho
            </button>
          </div>
        </div>
      ) : null}
    </Modal>
  );
}
