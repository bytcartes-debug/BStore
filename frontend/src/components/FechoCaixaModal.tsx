import { useState, useEffect, useCallback } from 'react';
import { RefreshCw, CreditCard, Printer, CheckCircle2, History } from 'lucide-react';
import { apiRequest } from '../utils/api';
import { decimalSeguro, formatMoney, parseDecimalInput } from '../utils/decimal';
import { Modal, Spinner, Notice, Field } from './UI';
import { useToast } from '../utils/toast';

interface FechoCaixaData {
  data: string;
  totalVendido: string;
  totalCusto: string;
  lucroEstimado: string;
  numeroVendas: number;
  vendasAnuladas: number;
  totalFiado: string;
  totalDespesas?: string;
  totaisPorMetodo: Record<string, string>;
}

interface SessaoCaixaAtual {
  aberta: boolean;
  id?: number;
  abertaEm?: string;
  valorInicial?: string;
  vendasDinheiro?: string;
  devolucoesDinheiro?: string;
  despesasDinheiro?: string;
  valorEsperado?: string;
  totalVendas?: number;
}

interface SessaoHistorico {
  id: number;
  abertaEm: string;
  fechadaEm?: string;
  valorInicial: string;
  valorEsperado?: string;
  valorContado?: string;
  diferenca?: string;
  notaFecho?: string;
  estado: string;
}

interface FechoCaixaModalProps {
  onClose: () => void;
  onSessaoFechada?: () => void;
}

export function FechoCaixaModal({ onClose, onSessaoFechada }: FechoCaixaModalProps) {
  const toast = useToast();
  const [abaAtiva, setAbaAtiva] = useState<'sessao' | 'diario' | 'historico'>('sessao');
  const [dataSelecionada, setDataSelecionada] = useState(
    new Date().toISOString().slice(0, 10),
  );

  // Sessão atual
  const [sessao, setSessao] = useState<SessaoCaixaAtual | null>(null);
  const [, setCarregandoSessao] = useState(false);
  const [valorContado, setValorContado] = useState('');
  const [notaFecho, setNotaFecho] = useState('');
  const [fechando, setFechando] = useState(false);
  const [fechoSucesso, setFechoSucesso] = useState<any | null>(null);

  // Relatório diário
  const [dados, setDados] = useState<FechoCaixaData | null>(null);
  const [carregandoDiario, setCarregandoDiario] = useState(false);

  // Histórico
  const [historico, setHistorico] = useState<SessaoHistorico[]>([]);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);

  const [erro, setErro] = useState<string | null>(null);

  const carregarSessao = useCallback(async () => {
    setCarregandoSessao(true);
    try {
      const res = await apiRequest<SessaoCaixaAtual>('/api/caixa/atual');
      setSessao(res);
      if (res && res.aberta && res.valorEsperado) {
        setValorContado(res.valorEsperado);
      } else {
        setAbaAtiva('diario');
      }
    } catch {
      setAbaAtiva('diario');
    } finally {
      setCarregandoSessao(false);
    }
  }, []);

  const carregarDiario = useCallback(async (data: string) => {
    setCarregandoDiario(true);
    setErro(null);
    try {
      const res = await apiRequest<FechoCaixaData>(`/api/caixa/fecho?data=${data}`);
      setDados(res);
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Falha ao carregar fecho diário.');
    } finally {
      setCarregandoDiario(false);
    }
  }, []);

  const carregarHistorico = useCallback(async () => {
    setCarregandoHistorico(true);
    try {
      const res = await apiRequest<SessaoHistorico[]>('/api/caixa/historico');
      setHistorico(res || []);
    } catch {
      setHistorico([]);
    } finally {
      setCarregandoHistorico(false);
    }
  }, []);

  useEffect(() => {
    void carregarSessao();
  }, [carregarSessao]);

  useEffect(() => {
    if (abaAtiva === 'diario') {
      void carregarDiario(dataSelecionada);
    } else if (abaAtiva === 'historico') {
      void carregarHistorico();
    }
  }, [abaAtiva, dataSelecionada, carregarDiario, carregarHistorico]);

  const valorEsperadoDec = decimalSeguro(sessao?.valorEsperado, 0);
  const valorContadoDec = parseDecimalInput(valorContado) || decimalSeguro(0);
  const diferencaDec = valorContadoDec.minus(valorEsperadoDec);

  const handleFecharSessao = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sessao || !sessao.aberta) return;
    setFechando(true);
    setErro(null);
    try {
      const res = await apiRequest('/api/caixa/fechar', {
        method: 'POST',
        body: JSON.stringify({
          sessaoId: sessao.id,
          valorContado: valorContadoDec.toFixed(2),
          notaFecho: notaFecho.trim() || undefined,
        }),
      });
      setFechoSucesso(res);
      toast('Caixa fechado com sucesso.');
      if (onSessaoFechada) onSessaoFechada();
    } catch (err: unknown) {
      setErro(err instanceof Error ? err.message : 'Falha ao fechar o caixa.');
    } finally {
      setFechando(false);
    }
  };

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
          <h2>BSTORE</h2>
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
            <tr><td>Saídas / Despesas:</td><td style="text-align:right;color:#ea580c">-${formatMoney(dados.totalDespesas || '0')}</td></tr>
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
    <Modal title="Controlo e Fecho de Caixa" onClose={onClose} wide>
      {/* Abas */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, borderBottom: '1px solid var(--border-color)', paddingBottom: 8 }}>
        {sessao && sessao.aberta && (
          <button
            type="button"
            className={abaAtiva === 'sessao' ? 'btn-primary' : 'btn-secondary'}
            onClick={() => setAbaAtiva('sessao')}
            style={{ fontSize: 13 }}
          >
            Fechar caixa agora
          </button>
        )}
        <button
          type="button"
          className={abaAtiva === 'diario' ? 'btn-primary' : 'btn-secondary'}
          onClick={() => setAbaAtiva('diario')}
          style={{ fontSize: 13 }}
        >
          Resumo diário
        </button>
        <button
          type="button"
          className={abaAtiva === 'historico' ? 'btn-primary' : 'btn-secondary'}
          onClick={() => setAbaAtiva('historico')}
          style={{ fontSize: 13 }}
        >
          <History size={14} style={{ marginRight: 4 }} /> Histórico de fechos
        </button>
      </div>

      {erro && <Notice>{erro}</Notice>}

      {/* ABA 1: FECHAR SESSÃO ATIVA */}
      {abaAtiva === 'sessao' && sessao && (
        <div>
          {fechoSucesso ? (
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <CheckCircle2 size={48} color="#16a34a" style={{ margin: '0 auto 12px' }} />
              <h3>Caixa fechado com sucesso!</h3>
              <p style={{ color: 'var(--text-secondary)' }}>
                Esperado: {formatMoney(fechoSucesso.valorEsperado)} | Contado: {formatMoney(fechoSucesso.valorContado)}
              </p>
              {fechoSucesso.diferenca !== '0.00' && (
                <p style={{ fontWeight: 600, color: Number(fechoSucesso.diferenca) < 0 ? '#dc2626' : '#2563eb' }}>
                  Diferença: {Number(fechoSucesso.diferenca) > 0 ? '+' : ''}{formatMoney(fechoSucesso.diferenca)}
                </p>
              )}
              <div style={{ marginTop: 20 }}>
                <button type="button" className="btn-primary" onClick={onClose}>
                  Concluir
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleFecharSessao}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10, marginBottom: 16 }}>
                <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Fundo Inicial</span>
                  <strong style={{ display: 'block', fontSize: 16 }}>{formatMoney(sessao.valorInicial || '0')}</strong>
                </div>
                <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Vendas Dinheiro</span>
                  <strong style={{ display: 'block', fontSize: 16, color: '#16a34a' }}>+{formatMoney(sessao.vendasDinheiro || '0')}</strong>
                </div>
                <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Devoluções</span>
                  <strong style={{ display: 'block', fontSize: 16, color: '#dc2626' }}>-{formatMoney(sessao.devolucoesDinheiro || '0')}</strong>
                </div>
                <div className="card" style={{ padding: 10, textAlign: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Despesas / Saídas</span>
                  <strong style={{ display: 'block', fontSize: 16, color: '#ea580c' }}>-{formatMoney(sessao.despesasDinheiro || '0')}</strong>
                </div>
                <div className="card" style={{ padding: 10, textAlign: 'center', background: 'var(--bg-subtle)' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Valor Esperado</span>
                  <strong style={{ display: 'block', fontSize: 18, color: 'var(--color-brand)' }}>{formatMoney(valorEsperadoDec)}</strong>
                </div>
              </div>

              <Field id="fecho-contado" label="Dinheiro contado na gaveta (MT) *">
                <input
                  id="fecho-contado"
                  type="number"
                  step="0.01"
                  min="0"
                  required
                  value={valorContado}
                  onChange={(e) => setValorContado(e.target.value)}
                  style={{ fontSize: 18, fontWeight: 600, padding: 10 }}
                />
              </Field>

              <div style={{ display: 'flex', gap: 8, marginTop: 8, marginBottom: 16 }}>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ fontSize: 12, padding: '4px 8px' }}
                  onClick={() => setValorContado(valorEsperadoDec.toFixed(2))}
                >
                  Usar valor esperado ({formatMoney(valorEsperadoDec)})
                </button>
              </div>

              {/* Indicador de diferença */}
              <div style={{ marginBottom: 16 }}>
                {diferencaDec.isZero() ? (
                  <Notice kind="success">A contagem confere exatamente com o valor esperado.</Notice>
                ) : diferencaDec.gt(0) ? (
                  <Notice kind="info">Sobra de dinheiro na gaveta: +{formatMoney(diferencaDec)}</Notice>
                ) : (
                  <Notice kind="error">Falta de dinheiro na gaveta: {formatMoney(diferencaDec)}</Notice>
                )}
              </div>

              <Field id="fecho-nota" label="Observações de fecho (opcional)">
                <input
                  id="fecho-nota"
                  type="text"
                  placeholder="Ex: Turno da manhã, falta explicada por..."
                  value={notaFecho}
                  onChange={(e) => setNotaFecho(e.target.value)}
                />
              </Field>

              <div className="modal-actions" style={{ marginTop: 20 }}>
                <button type="button" className="btn-secondary" onClick={onClose} disabled={fechando}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={fechando}>
                  {fechando ? <Spinner size="small" /> : 'Confirmar e fechar caixa'}
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* ABA 2: RESUMO DIÁRIO */}
      {abaAtiva === 'diario' && (
        <div>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
            <label htmlFor="fecho-data" style={{ fontWeight: 600 }}>
              Data:
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
              onClick={() => void carregarDiario(dataSelecionada)}
              disabled={carregandoDiario}
            >
              {carregandoDiario ? <Spinner size="small" /> : <><RefreshCw size={15} /> <span>Atualizar</span></>}
            </button>
          </div>

          {carregandoDiario && !dados ? (
            <div style={{ padding: 32, textAlign: 'center' }}>
              <Spinner />
              <p style={{ marginTop: 8 }}>A carregar dados do dia…</p>
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
                  <span style={{ fontSize: 10, color: 'var(--text-muted, #888)', display: 'block', marginTop: 2 }}>
                    Vendas antigas sem custo não entram no lucro
                  </span>
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

                <div className="card" style={{ padding: 12, textAlign: 'center' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-muted, #666)' }}>Saídas / Despesas</span>
                  <strong style={{ display: 'block', fontSize: 18, color: '#ea580c' }}>
                    -{formatMoney(dados.totalDespesas || '0')}
                  </strong>
                </div>
              </div>

              <div className="card" style={{ padding: 16 }}>
                <h4 style={{ margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <CreditCard size={18} /> Totais por Forma de Pagamento
                </h4>
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
                  <Printer size={16} /> Imprimir Fecho
                </button>
              </div>
            </div>
          ) : null}
        </div>
      )}

      {/* ABA 3: HISTÓRICO DE SESSÕES */}
      {abaAtiva === 'historico' && (
        <div>
          {carregandoHistorico ? (
            <div style={{ textAlign: 'center', padding: 24 }}><Spinner /></div>
          ) : historico.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 24 }}>
              Nenhum fecho de caixa registado até ao momento.
            </p>
          ) : (
            <div className="table-wrapper">
              <table className="responsive-table">
                <thead>
                  <tr>
                    <th>Data Abertura</th>
                    <th>Fechada Em</th>
                    <th className="numeric">Inicial</th>
                    <th className="numeric">Esperado</th>
                    <th className="numeric">Contado</th>
                    <th className="numeric">Diferença</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {historico.map((h) => {
                    const difNum = Number(h.diferenca || '0');
                    return (
                      <tr key={h.id}>
                        <td>{h.abertaEm ? h.abertaEm.replace('T', ' ').slice(0, 16) : '-'}</td>
                        <td>{h.fechadaEm ? h.fechadaEm.replace('T', ' ').slice(0, 16) : 'Aberta'}</td>
                        <td className="numeric font-mono">{formatMoney(h.valorInicial)}</td>
                        <td className="numeric font-mono">{h.valorEsperado ? formatMoney(h.valorEsperado) : '-'}</td>
                        <td className="numeric font-mono">{h.valorContado ? formatMoney(h.valorContado) : '-'}</td>
                        <td className="numeric font-mono" style={{ color: difNum < 0 ? '#dc2626' : difNum > 0 ? '#2563eb' : '#16a34a', fontWeight: 600 }}>
                          {h.diferenca ? `${difNum > 0 ? '+' : ''}${formatMoney(h.diferenca)}` : '-'}
                        </td>
                        <td>
                          <span className={`badge ${h.estado === 'ABERTA' ? 'badge-success' : 'badge-neutral'}`}>
                            {h.estado === 'ABERTA' ? 'Aberta' : 'Fechada'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <div className="modal-actions" style={{ marginTop: 20 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              Fechar
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}
