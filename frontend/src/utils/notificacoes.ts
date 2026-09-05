import { LocalNotifications } from '@capacitor/local-notifications';

// Pedir permissão ao utilizador (chamar 1x no arranque)
export async function pedirPermissaoNotificacoes() {
  const { display } = await LocalNotifications.requestPermissions();
  return display === 'granted';
}

// Verifica stock baixo e dispara notificações
export async function verificarStockBaixo() {
  try {
    const res = await fetch('/api/produtos');
    const produtos: any[] = await res.json();

    const baixo = produtos.filter(p => p.stock <= p.stockMinimo);
    if (baixo.length === 0) return;

    // Cancela notificações de stock anteriores para não repetir
    const pendentes = await LocalNotifications.getPending();
    const stockIds = pendentes.notifications
      .filter(n => n.id >= 1000 && n.id < 2000)
      .map(n => ({ id: n.id }));
    if (stockIds.length > 0) await LocalNotifications.cancel({ notifications: stockIds });

    // Dispara uma notificação agrupada
    await LocalNotifications.schedule({
      notifications: [
        {
          id: 1000,
          title: `⚠️ Stock Baixo — ${baixo.length} produto(s)`,
          body: baixo.map(p => `• ${p.nome}: ${p.stock} em stock`).join('\n'),
          schedule: { at: new Date(Date.now() + 500) },
          sound: undefined,
          smallIcon: 'ic_stat_icon_config_sample',
          iconColor: '#f59e0b',
          actionTypeId: 'ABRIR_PRODUTOS',
          extra: { pagina: 'produtos' },
        },
      ],
    });
  } catch {
    // Sem internet ou servidor offline — ignora silenciosamente
  }
}

// Notificação de venda registada com sucesso
export async function notificarVendaRegistada(produto: string, total: number) {
  await LocalNotifications.schedule({
    notifications: [
      {
        id: 2000 + Math.floor(Math.random() * 1000),
        title: '✅ Venda Registada',
        body: `${produto} — MT ${total.toFixed(2)}`,
        schedule: { at: new Date(Date.now() + 300) },
        sound: undefined,
        smallIcon: 'ic_stat_icon_config_sample',
        iconColor: '#10b981',
      },
    ],
  });
}
