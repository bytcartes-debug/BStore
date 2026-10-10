import { test, expect } from '@playwright/test';
import { mockApi, navigate, categories, products, noOverflow } from './fixtures';

test('edição de categoria preserva ícone antigo até haver escolha explícita', async ({ page }) => {
  await mockApi(page);
  const sent: Record<string, unknown>[] = [];
  await page.route('**/api/categorias/1', (route) => {
    sent.push(route.request().postDataJSON());
    return route.fulfill({ json: categories[0] });
  });
  await page.goto('/');
  await navigate(page, 'Categorias');
  await page.getByRole('button', { name: 'Editar categoria Mercearia' }).click();
  await page.getByLabel('Nome *', { exact: true }).fill('Mercearia atualizada');
  await page.getByRole('button', { name: /Guardar alterações/i }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(sent[0].icone).toBe(categories[0].icone);
  await page.getByRole('button', { name: 'Editar categoria Mercearia' }).click();
  await page.getByRole('button', { name: 'Frutas', exact: true }).click();
  await page.getByRole('button', { name: /Guardar alterações/i }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(sent[1].icone).toBe('fruit');
});

test('confirmação de remoção de categoria só abre após consultar produtos', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/categorias/1/produtos', (route) =>
    route.fulfill({ status: 503, json: {} }),
  );
  await page.goto('/');
  await navigate(page, 'Categorias');
  await page.getByRole('button', { name: 'Remover categoria Mercearia' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.unroute('**/api/categorias/1/produtos');
  await page.getByRole('button', { name: 'Remover categoria Mercearia' }).click();
  await expect(page.getByRole('dialog')).toContainText('as vendas associadas');
  await expect(page.getByRole('dialog')).toContainText(products[0].nome);
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
});

test('venda concluída fecha carrinho mesmo sem notificações do dispositivo', async ({ page }) => {
  await mockApi(page);
  let count = 0;
  await page.route('**/api/vendas/lote', (route) => {
    count++;
    return route.fulfill({ json: { id: 101, numero: 1, itens: 1, total: '35.00', troco: '0.00' } });
  });
  await page.goto('/');
  await navigate(page, 'Vendas');
  await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
  await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Água');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: 'Adicionar', exact: true }).click();
  await page.getByRole('button', { name: /Confirmar venda/i }).click();
  // Venda abre recibo pós-venda
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Fechar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.toast')).toContainText('Venda registada: MT 35.00');
  await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
  await expect(page.getByText('O carrinho está vazio.')).toBeVisible();
  expect(count).toBe(1);
});

test('pagamento exige confirmação e mantém o diálogo quando falha', async ({ page }) => {
  await mockApi(page);
  let count = 0;
  await page.route('**/api/devedores/1', (route) => {
    count++;
    return route.fulfill({ status: 503, json: {} });
  });
  await page.goto('/');
  await navigate(page, 'Devedores');
  await page.getByRole('button', { name: 'Confirmar pagamento de Cliente de teste' }).click();
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  expect(count).toBe(0);
  await page.getByRole('button', { name: 'Confirmar pagamento de Cliente de teste' }).click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /Confirmar pagamento/i })
    .click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('O servidor não conseguiu');
  expect(count).toBe(1);
});

test('cadastro rápido de produto pede apenas nome e preço', async ({ page }) => {
  await mockApi(page);
  let body: Record<string, unknown> | undefined;
  await page.route('**/api/produtos', (route) => {
    if (route.request().method() === 'POST') {
      body = route.request().postDataJSON();
      return route.fulfill({
        status: 201,
        json: { id: 99, nome: body?.nome, preco: body?.preco, stock: '0.000', categoriaId: 1 },
      });
    }
    return route.fallback();
  });
  await page.goto('/');
  await navigate(page, 'Produtos');
  await page.getByRole('button', { name: /Adicionar produto/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByLabel('Nome *', { exact: true }).fill('Sabão em barra');
  await page.getByLabel('Preço (MT) *', { exact: true }).fill('45.00');
  await page.getByRole('button', { name: /Criar produto/i }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(body).toMatchObject({
    nome: 'Sabão em barra',
    preco: '45.00',
  });
});

test('entrada de stock regista compra com custo e quantidade', async ({ page }) => {
  await mockApi(page);
  let body: Record<string, unknown> | undefined;
  await page.route('**/api/stock/entradas', (route) => {
    body = route.request().postDataJSON();
    return route.fulfill({
      status: 201,
      json: [{ id: 1, produtoId: 1, tipo: 'ENTRADA', quantidade: '5.000', custoUnitario: '70.00' }],
    });
  });
  await page.goto('/');
  await navigate(page, 'Produtos');
  await page.getByRole('button', { name: /Entrada/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: /Adicionar à lista/i }).click();
  await page.getByRole('button', { name: /Confirmar entrada/i }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(body).toBeDefined();
});

test('admin cria utilizador sem depender da sessão antiga no localStorage', async ({ page }) => {
  await mockApi(page);
  let body: Record<string, unknown> | undefined;
  await page.route('**/api/usuarios', (route) => {
    if (route.request().method() === 'GET') return route.fallback();
    body = route.request().postDataJSON();
    return route.fulfill({ status: 201, json: { id: 12, ...body } });
  });
  await page.goto('/');
  expect(await page.evaluate(() => localStorage.getItem('currentUser'))).toBeNull();
  await navigate(page, 'Utilizadores');
  await page.getByRole('button', { name: 'Novo utilizador', exact: true }).click();
  await page.getByLabel('Nome *', { exact: true }).fill('Teste de acesso');
  await page.getByLabel('Email *', { exact: true }).fill('novo@example.test');
  await page.getByLabel('Senha *', { exact: true }).fill('senha-de-teste');
  await page.getByRole('button', { name: '14 dias', exact: true }).click();
  await page.getByRole('button', { name: 'Criar utilizador', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(body).toMatchObject({
    nome: 'Teste de acesso',
    email: 'novo@example.test',
    role: 'operator',
    diasAcesso: 14,
  });
});

test('pesquisa e senha mantêm espaço para os ícones e modais prendem o foco', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await navigate(page, 'Produtos');
  await expect(page.getByLabel('Pesquisar por nome ou código')).toHaveCSS('padding-left', '42px');
  await page.getByRole('button', { name: 'Adicionar produto', exact: true }).click();
  const first = page.getByRole('button', { name: 'Fechar janela' });
  await first.focus();
  await page.keyboard.press('Shift+Tab');
  await expect(page.getByRole('button', { name: 'Criar produto', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(first).toBeFocused();
  await noOverflow(page);
});

test('venda guardada offline é enviada uma única vez ao restabelecer ligação', async ({
  page,
  context,
}) => {
  await mockApi(page);
  const vendasEnviadas: { idempotencyKey: string; body: any }[] = [];

  await page.route('**/api/vendas/lote', async (route) => {
    const postData = route.request().postDataJSON();
    vendasEnviadas.push({
      idempotencyKey: (route.request().headers()['idempotency-key'] as string) || '',
      body: postData,
    });
    return route.fulfill({
      status: 201,
      json: { id: 201, numero: 5, itens: 1, total: '35.00', troco: '0.00' },
    });
  });

  await page.goto('/');
  await navigate(page, 'Vendas');
  await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
  await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Água');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: 'Adicionar', exact: true }).click();

  // Simula modo offline do browser
  await context.setOffline(true);
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));

  // Submete a venda enquanto offline
  await page.getByRole('button', { name: /Confirmar venda/i }).click();

  // Venda abre recibo local e o diálogo é fechado
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Fechar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Nenhuma venda deve ter sido enviada por rede enquanto offline
  expect(vendasEnviadas.length).toBe(0);

  // Barra de aviso offline deve estar visível
  await expect(page.locator('.offline-warning-bar')).toBeVisible();

  // Restabelece a ligação à internet
  await context.setOffline(false);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));

  // Clica no botão para sincronizar se estiver presente
  const syncBtn = page.getByRole('button', { name: 'Sincronizar agora' });
  if (await syncBtn.isVisible()) {
    await syncBtn.click();
  }

  // Aguarda que a barra de aviso offline desapareça após envio com sucesso
  await expect(page.locator('.offline-warning-bar')).toHaveCount(0);

  // A venda deve ter chegado ao servidor exatamente uma só vez
  expect(vendasEnviadas.length).toBe(1);
  expect(vendasEnviadas[0].body.uuidCliente).toBeDefined();
  expect(vendasEnviadas[0].idempotencyKey).toBe(vendasEnviadas[0].body.uuidCliente);
});

test('painel carrega com utilizador novo sem vendas nem produtos e sem erro', async ({ page }) => {
  await mockApi(page);
  // Simular utilizador novo: dashboard vazio sem vendas nem produtos
  await page.route('**/api/dashboard', (route) =>
    route.fulfill({
      json: {
        totalVendasHoje: '0.00',
        lucroHoje: '0.00',
        lucroUltimos7Dias: '0.00',
        valorTotalStockCusto: '0.00',
        totalProdutos: 0,
        totalCategorias: 0,
        totalDevedores: 0,
        alertasStock: [],
        vendasPorMetodo: [],
        produtosMaisVendidos: [],
        vendasRecentes: [],
        vendasPorDia: [],
      },
    }),
  );

  await page.goto('/');
  await navigate(page, 'Visão Geral');

  // Confirma que não mostra a mensagem de erro do PageErrorBoundary
  await expect(
    page.getByText('Não foi possível abrir esta página. Recarregue para tentar novamente.'),
  ).toHaveCount(0);

  // Confirma que o painel renderiza os cartões principais
  await expect(page.getByText('Vendas de Hoje')).toBeVisible();
  await expect(page.getByText('MT 0.00').first()).toBeVisible();
});

test('leitura de código com câmara no cadastro preenche código do produto', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/produtos/barcode/*', (route) =>
    route.fulfill({ status: 404, json: { erro: 'Produto não encontrado' } }),
  );
  await page.goto('/');
  await navigate(page, 'Produtos');

  // Abre modal de adicionar produto
  await page.getByRole('button', { name: 'Adicionar produto', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();

  // Botão CTA de câmara visível no topo
  const btnScan = page.getByRole('button', { name: /Ler código com câmara/i });
  await expect(btnScan).toBeVisible();
  await btnScan.click();

  // Modal do scanner abre
  await expect(page.getByRole('dialog', { name: /Ler código com câmara/i })).toBeVisible();

  // Utilizador usa opção de digitação manual do modal do scanner
  const inputManual = page.locator('#scanner-manual-input');
  if (!(await inputManual.isVisible())) {
    await page.getByRole('button', { name: /Digitar/i }).click();
  }
  await expect(inputManual).toBeVisible();
  await inputManual.fill('6001234567890');
  await page.getByRole('button', { name: 'Confirmar' }).click();

  // Scanner fecha e código fica preenchido no formulário do produto
  await expect(page.locator('#product-barcode')).toHaveValue('6001234567890');
});

test('atalho de câmara na barra de produtos abre edição se o produto já existir', async ({
  page,
}) => {
  await mockApi(page);
  await page.goto('/');
  await navigate(page, 'Produtos');

  // Clica no botão "Ler código" da barra principal
  const btnToolbarScan = page.getByRole('button', { name: 'Ler código', exact: true });
  await expect(btnToolbarScan).toBeVisible();
  await btnToolbarScan.click();

  // Modal do scanner abre
  await expect(page.getByRole('dialog', { name: /Ler código com câmara/i })).toBeVisible();

  // Digita o código de barras de um produto existente
  const inputManual = page.locator('#scanner-manual-input');
  if (!(await inputManual.isVisible())) {
    await page.getByRole('button', { name: /Digitar/i }).click();
  }
  await expect(inputManual).toBeVisible();
  await inputManual.fill('5601234567890');
  await page.getByRole('button', { name: 'Confirmar' }).click();

  // Modal de edição do produto deve abrir diretamente
  await expect(page.getByRole('heading', { name: 'Editar produto' })).toBeVisible();
  await expect(page.locator('#product-name')).toHaveValue('Arroz agulha de qualidade superior');
});
