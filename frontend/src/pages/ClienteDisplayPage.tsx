import { useEffect, useState } from 'react';
import './ClienteDisplayPage.css';

interface DisplayItem {
  nome: string;
  quantidade: string;
  precoUnitario: string;
  total: string;
}

interface SaleSuccessData {
  total: string;
  pago: string;
  troco: string;
}

export default function ClienteDisplayPage() {
  const [horaAtual, setHoraAtual] = useState('');
  const [itens, setItens] = useState<DisplayItem[]>([]);
  const [total, setTotal] = useState('0.00');
  const [nomeLoja, setNomeLoja] = useState('BStore');
  const [sucesso, setSucesso] = useState<SaleSuccessData | null>(null);

  useEffect(() => {
    const updateTime = () => {
      const agora = new Date();
      setHoraAtual(
        agora.toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!('BroadcastChannel' in window)) return;
    const channel = new BroadcastChannel('bstore_cliente_display');

    channel.onmessage = (event) => {
      const data = event.data;
      if (!data) return;

      if (data.type === 'cart_update') {
        setSucesso(null);
        setItens(data.items || []);
        setTotal(data.total || '0.00');
        if (data.nomeLoja) setNomeLoja(data.nomeLoja);
      } else if (data.type === 'sale_success') {
        setSucesso({
          total: data.total || '0.00',
          pago: data.pago || '0.00',
          troco: data.troco || '0.00',
        });
        setItens([]);
        setTotal('0.00');
        // Volta para tela inicial após 6 segundos
        setTimeout(() => {
          setSucesso(null);
        }, 6000);
      } else if (data.type === 'idle') {
        setSucesso(null);
        setItens([]);
        setTotal('0.00');
      }
    };

    return () => {
      channel.close();
    };
  }, []);

  if (sucesso) {
    return (
      <div className="cliente-display-container sucesso-mode">
        <div className="cliente-display-card">
          <div className="sucesso-icone">✓</div>
          <h1 className="sucesso-titulo">Obrigado pela sua preferência!</h1>
          <div className="sucesso-dados">
            <div className="sucesso-linha">
              <span>Total Pago:</span>
              <strong>{sucesso.total} MT</strong>
            </div>
            {sucesso.troco !== '0.00' && (
              <div className="sucesso-linha troco-destaque">
                <span>O seu Troco:</span>
                <strong>{sucesso.troco} MT</strong>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  const temItens = itens.length > 0;

  return (
    <div className="cliente-display-container">
      <header className="cliente-display-header">
        <div className="loja-marca">
          <span className="loja-nome">{nomeLoja}</span>
        </div>
        <div className="relogio-display">{horaAtual}</div>
      </header>

      {temItens ? (
        <main className="cliente-display-body">
          <div className="cliente-tabela-scroll">
            <table className="cliente-itens-tabela">
              <thead>
                <tr>
                  <th>Produto</th>
                  <th className="num">Qtd</th>
                  <th className="num">Preço</th>
                  <th className="num">Total</th>
                </tr>
              </thead>
              <tbody>
                {itens.map((item, idx) => (
                  <tr key={idx}>
                    <td className="item-nome">{item.nome}</td>
                    <td className="num">{item.quantidade}</td>
                    <td className="num">{item.precoUnitario} MT</td>
                    <td className="num item-total">{item.total} MT</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="cliente-display-footer">
            <div className="total-label">Total a Pagar</div>
            <div className="total-valor">{total} <span className="moeda">MT</span></div>
          </footer>
        </main>
      ) : (
        <main className="cliente-display-idle">
          <div className="idle-conteudo">
            <h2>Bem-vindo à nossa loja!</h2>
            <p>O seu atendimento começará em instantes.</p>
            <div className="idle-hora">{horaAtual}</div>
          </div>
        </main>
      )}
    </div>
  );
}
