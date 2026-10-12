# BStore — Guia de Verificação Manual por Dispositivo

Este documento contém a lista de verificação para validação manual de campo do BStore em diferentes formatos e plataformas.

---

## 1. iPhone com Safari (Navegador Móvel)

Plataforma de teste: iPhone (iOS 16+ / 17+ / 18+) no Safari.

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 1.1 | Login | Autenticação rápida com teclado numérico/alfanumérico; sem zoom indevido (font-size >= 16px). | [ ] |
| 1.2 | Venda em dinheiro | Adição de produto, atalho de nota (ex: 100 MT), cálculo instantâneo do troco em 3 toques. | [ ] |
| 1.3 | Venda M-Pesa | Registo de pagamento digital e inserção do código de confirmação. | [ ] |
| 1.4 | Venda a Fiado | Seleção obrigatória de cliente devedor; cálculo automático do saldo devedor. | [ ] |
| 1.5 | Devolução | Abertura dos detalhes da venda e devolução parcial com reposição no stock. | [ ] |
| 1.6 | Caixa (abrir/fechar) | Registo de fundo de trocos e conferência cega no fecho da sessão. | [ ] |
| 1.7 | Scanner de câmara | Pedido de permissão transparente; leitura de códigos de barras (fallback JS/ZXing). | [ ] |
| 1.8 | Recibo (impressão/partilha) | Abertura de impressão térmica limpa (window.print) e partilha via WhatsApp (Web Share API). | [ ] |
| 1.9 | Tema escuro | Contraste legível sem perda de foco; campos com fundo escuro. | [ ] |
| 1.10 | Rotação de ecrã | Transição fluida entre vertical e horizontal sem quebra de layout. | [ ] |
| 1.11 | Teclado sobre campos | O campo focado permanece visível acima do teclado virtual. | [ ] |

---

## 2. iPhone Instalado como App (PWA no Ecrã Principal)

Plataforma de teste: Adicionado ao Ecrã Principal a partir do Safari (display-mode: standalone).

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 2.1 | Instalação e Abertura | Ícone maskable no ecrã inicial; abertura em ecrã total sem barra de navegação do Safari. | [ ] |
| 2.2 | Safe Areas e Notch | Respeito pelos insets do notch e barra inferior de gestos (env(safe-area-inset-*)). | [ ] |
| 2.3 | Overscroll de Vendas | Evita pull-to-refresh acidental durante o registo de venda. | [ ] |
| 2.4 | Venda completa | Fluxo de venda em dinheiro e emissão de recibo. | [ ] |
| 2.5 | Venda M-Pesa | Registo com recibo térmico e envio direto. | [ ] |
| 2.6 | Fiado e Devedores | Gestão de cliente devedor e amortização de dívida. | [ ] |
| 2.7 | Devolução parcial | Seleção de quantidade e estorno da dívida/dinheiro. | [ ] |
| 2.8 | Controlo de caixa | Abertura, registo de despesa e encerramento diário. | [ ] |
| 2.9 | Scanner de câmara | Leitura fluida no modo PWA standalone. | [ ] |
| 2.10 | Recibo e partilha | Partilha nativa com ficheiro ou mensagem de WhatsApp. | [ ] |
| 2.11 | Rotação e teclado | Interface adapta-se à rotação e teclado não cobre botões de confirmação. | [ ] |

---

## 3. Android com Chrome (Navegador Móvel)

Plataforma de teste: Smartphone Android (Chrome 110+).

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 3.1 | Banner de instalação | Botão "Instalar app" visível no Header/Perfil via beforeinstallprompt. | [ ] |
| 3.2 | Login | Acesso ao sistema sem flash branco nem scroll horizontal. | [ ] |
| 3.3 | Venda em dinheiro | Fluxo rápido de 3 toques com vibração tátil (navigator.vibrate). | [ ] |
| 3.4 | Venda M-Pesa / e-Mola | Seleção rápida por atalho numérico ou toque. | [ ] |
| 3.5 | Fiado | Adição rápida de novo cliente direto no modal de pagamento. | [ ] |
| 3.6 | Devolução parcial | Atualização imediata do histórico e estado da venda. | [ ] |
| 3.7 | Caixa diário | Fecho cego com cálculo automático de quebra/sobra. | [ ] |
| 3.8 | Scanner de câmara | Leitura de códigos EAN-13, QR Code e Code-128. | [ ] |
| 3.9 | Recibo | Impressão Bluetooth ou térmica e link direto para WhatsApp. | [ ] |
| 3.10 | Tema escuro | Alternância de tema respeita as preferências do sistema. | [ ] |
| 3.11 | Teclado e rotação | Comportamento estável ao alternar entre vertical e horizontal. | [ ] |

---

## 4. Android com App Nativa (Capacitor APK)

Plataforma de teste: APK compilado instalado no dispositivo Android (Android 10, 12, 14+).

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 4.1 | Inicialização e Splash | SplashScreen da cor da marca centralizada; carregamento sem ecrã branco. | [ ] |
| 4.2 | StatusBar nativa | Cor de fundo coerente com o tema e ícones com contraste adequado. | [ ] |
| 4.3 | Botão Voltar Físico | Fecha primeiro modais/sheets; pede confirmação ao tentar sair da aplicação. | [ ] |
| 4.4 | Proteção de pagamento | Bloqueio de saída acidental durante o processo de pagamento. | [ ] |
| 4.5 | KeepAwake nas Vendas | Ecrã permanece aceso e ativo durante a operação no balcão de vendas. | [ ] |
| 4.6 | Leitor ML Kit | Scanner nativo ultrarrápido com permissão de câmara gerida pelo sistema. | [ ] |
| 4.7 | Venda completa e troco | Operação em dinheiro com atalhos de notas e teclado numérico integrado. | [ ] |
| 4.8 | Venda digital e fiado | Conclusão sem falhas de conectividade. | [ ] |
| 4.9 | Devolução e reposição | Reintegração automática de stock. | [ ] |
| 4.10 | Abertura e fecho de caixa | Relatório detalhado com confetes de celebração. | [ ] |
| 4.11 | Partilha externa | Links externos (WhatsApp, telefone) abrem fora do WebView. | [ ] |

---

## 5. Tablet (iPad / Android Tablet)

Plataforma de teste: Dispositivo com ecrã de 900 px a 1199 px de largura.

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 5.1 | Layout e Navegação | Menu adaptativo (sidebar lateral ou barra superior) sem quebras. | [ ] |
| 5.2 | Catálogo e Balcão POS | Grelha visual de produtos e resumo do carrinho lado a lado. | [ ] |
| 5.3 | Venda com 3 toques | Seleção de produto no catálogo, atalho de valor e confirmação. | [ ] |
| 5.4 | Pagamento dividido | Configuração de múltiplas formas de pagamento em simultâneo. | [ ] |
| 5.5 | Tela do cliente | Possibilidade de projetar ou abrir a tela do cliente. | [ ] |
| 5.6 | Devolução parcial | Janela modal ampla com tabela responsiva e controlos táteis (+ / -). | [ ] |
| 5.7 | Fecho de caixa | Resumo diário com visualização de histórico em abas. | [ ] |
| 5.8 | Acessibilidade e foco | Navegação por teclado externo ou suporte a rato/toque. | [ ] |
| 5.9 | Tema claro e escuro | Leitura equilibrada de gráficos e dados numéricos. | [ ] |
| 5.10 | Sem scroll horizontal | scrollWidth menor ou igual a innerWidth em qualquer orientação. | [ ] |

---

## 6. PC Desktop (Google Chrome & Microsoft Edge)

Plataforma de teste: Portátil ou computador de secretária (1280x800 px até 1920x1080 px).

| Item | Ação a testar | Critério de Aceitação | Estado |
|---|---|---|---|
| 6.1 | Atalhos de teclado | F2 ou / para pesquisa, 1 a 5 para formas de pagamento, Enter para confirmação. | [ ] |
| 6.2 | Navegação por teclado | Acesso total a todos os campos e botões via Tab, Shift+Tab e Esc. | [ ] |
| 6.3 | Venda em dinheiro com troco | Operação com digitação rápida no teclado físico e confirmação com Enter. | [ ] |
| 6.4 | Venda dividida | Adição de linhas de pagamento e distribuição dos valores. | [ ] |
| 6.5 | Devolução parcial | Processamento ágil com justificativo obrigatório. | [ ] |
| 6.6 | Abertura e fecho de caixa | Exportação de relatórios em CSV e conferência detalhada. | [ ] |
| 6.7 | Gráficos do Dashboard | Renderização assíncrona do Recharts com carregamento sob demanda. | [ ] |
| 6.8 | Impressão de recibo | Pré-visualização de impressão limpa formatada para rolos de 58 mm e 80 mm. | [ ] |
| 6.9 | Zoom e redimensionamento | Layout íntegro de 100% a 200% de zoom do navegador. | [ ] |
| 6.10 | Acessibilidade (WCAG 2.1 AA) | Leitores de ecrã recebem atualizações de totais e troco em aria-live. | [ ] |
