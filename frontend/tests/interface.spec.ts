import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mockApi, navigate, noOverflow, products, account, dashboard } from './fixtures';

for (const width of [320, 375, 600, 768, 900, 1024, 1440]) {
  test(`páginas e formulários sem overflow a ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await mockApi(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
    await noOverflow(page);
    if ([375, 1440].includes(width))
      await page.screenshot({
        path: testInfo.outputPath(`dashboard-${width}.png`),
        fullPage: true,
      });
    for (const [section, action] of [
      ['Produtos', 'Adicionar produto'],
      ['Categorias', 'Nova categoria'],
      ['Vendas', 'Registar venda'],
      ['Devedores', 'Registar dívida'],
      ['Utilizadores', 'Novo utilizador'],
    ]) {
      await navigate(page, section);
      await expect(
        page.getByRole('heading', { name: new RegExp(section, 'i'), level: 2 }),
      ).toBeVisible();
      await noOverflow(page);
      await page.getByRole('button', { name: action, exact: true }).first().click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await noOverflow(page);
      await page.getByRole('button', { name: 'Fechar janela' }).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    }
    await page.getByRole('button', { name: /Abrir perfil de/ }).click();
    await expect(page.getByRole('heading', { name: 'Perfil e segurança', level: 2 })).toBeVisible();
    await noOverflow(page);
  });
}

for (const dark of [false, true]) {
  test(`acessibilidade das páginas e modais: ${dark ? 'escuro' : 'claro'}`, async ({ page }) => {
    if (dark) await page.addInitScript(() => localStorage.setItem('bstore:theme', 'dark'));
    await mockApi(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
    const check = async () => {
      const result = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      expect(
        result.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) })),
      ).toEqual([]);
    };
    await check();
    for (const [section, action] of [
      ['Produtos', 'Adicionar produto'],
      ['Categorias', 'Nova categoria'],
      ['Vendas', 'Registar venda'],
      ['Devedores', 'Registar dívida'],
      ['Utilizadores', 'Novo utilizador'],
    ]) {
      await navigate(page, section);
      await expect(page.getByRole('heading', { name: section, level: 2 })).toBeVisible();
      await check();
      await page.getByRole('button', { name: action, exact: true }).first().click();
      await check();
      await page.getByRole('button', { name: 'Fechar janela' }).click();
    }
    await page.getByRole('button', { name: /Abrir perfil de/ }).click();
    await check();
  });
}

test('carregamento central sem flash de login, com movimento reduzido', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockApi(page);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/auth/me', async (route) => {
    await gate;
    await route.fulfill({ json: account });
  });
  await page.goto('/');
  await expect(page.getByText('A carregar o BStore…')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Bem-vindo de volta' })).toHaveCount(0);
  await expect(page.locator('.spinner')).toHaveCSS('animation-name', 'none');
  const box = await page.locator('.loading-screen').boundingBox();
  expect(box?.height).toBe(page.viewportSize()?.height);
  release();
  await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
});

test('falha de sessão permite tentar novamente sem mostrar login indevido', async ({ page }) => {
  await mockApi(page);
  let fail = true;
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: fail ? 500 : 200, json: fail ? { erro: 'interno' } : account }),
  );
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Não foi possível verificar a sessão');
  await expect(page.getByRole('heading', { name: 'Bem-vindo de volta' })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
});

test('login móvel, mostrar senha, erro e ausência de duplicados', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 812 });
  await mockApi(page);
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 401, json: { erro: 'Sessão inválida.' } }),
  );
  let attempts = 0;
  await page.route('**/api/auth/login', async (route) => {
    attempts++;
    await new Promise((resolve) => setTimeout(resolve, 400));
    await route.fulfill({ status: 401, json: { erro: 'Email ou senha incorretos.' } });
  });
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill('teste@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('senha-de-teste');
  await page.getByRole('button', { name: 'Mostrar senha' }).click();
  await expect(page.locator('#login-password')).toHaveAttribute('type', 'text');
  await noOverflow(page);
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(result.violations).toEqual([]);
  await page.getByRole('button', { name: 'Entrar na minha loja' }).click();
  await expect(page.getByRole('button', { name: 'A entrar…' })).toBeDisabled();
  await expect(page.getByRole('alert')).toContainText('Email ou senha incorretos.');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('teste@example.test');
  expect(attempts).toBe(1);
});

test('menu móvel gere foco, Escape e acesso a todas as páginas', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await mockApi(page, 'operator');
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'A sua loja, hoje' })).toBeVisible();
  const opener = page.getByRole('button', { name: 'Abrir menu' });
  await opener.click();
  await expect(page.getByRole('dialog', { name: 'Menu de navegação' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Utilizadores', exact: true })).toHaveCount(0);
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(result.violations).toEqual([]);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(opener).toBeFocused();
});

test('produto mantém formulário após erro, bloqueia repetição e restaura foco', async ({
  page,
}) => {
  await mockApi(page);
  let attempts = 0;
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/produtos', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fallback();
      return;
    }
    attempts++;
    await gate;
    await route.fulfill({ status: 500, json: { erro: 'SQL secret must not appear' } });
  });
  await page.goto('/');
  await navigate(page, 'Produtos');
  const opener = page.getByRole('button', { name: 'Adicionar produto', exact: true });
  await opener.click();
  await page.getByLabel('Nome *', { exact: true }).fill('Produto de teste');
  await page.getByLabel('Preço (MT) *').fill('12.50');
  await page.getByRole('button', { name: 'Criar produto', exact: true }).click();
  await expect(page.getByRole('button', { name: 'A guardar…' })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeVisible();
  release();
  await expect(page.getByRole('alert')).toContainText(
    'O servidor não conseguiu concluir o pedido.',
  );
  await expect(page.getByLabel('Nome *', { exact: true })).toHaveValue('Produto de teste');
  await expect(page.getByText('SQL secret must not appear')).toHaveCount(0);
  expect(attempts).toBe(1);
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(opener).toBeFocused();
});

test('pesquisa, filtros, valores e símbolos das categorias', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await navigate(page, 'Produtos');
  await page.getByLabel('Stock', { exact: true }).selectOption('low');
  await expect(page.getByText('Açúcar branco', { exact: true })).toBeVisible();
  await expect(page.getByText(products[0].nome, { exact: true })).toHaveCount(0);
  await page.getByLabel('Pesquisar por nome ou código').fill('inexistente');
  await expect(
    page.getByRole('heading', { name: 'Nenhum produto corresponde à pesquisa' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Limpar filtros' }).click();
  await page.getByLabel('Pesquisar por nome ou código').fill('5601234567890');
  await expect(page.getByText(products[0].nome, { exact: true })).toBeVisible();
  await navigate(page, 'Categorias');
  await expect(page.locator('.category-icon .category-emoji')).toHaveCount(2);
  await page.getByRole('button', { name: 'Editar categoria Mercearia' }).click();
  await expect(page.getByRole('button', { name: 'Compras', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});

test('carrinho móvel por teclado, cálculo decimal e falha sem perda de dados', async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await mockApi(page);
  let posted: unknown;
  await page.route('**/api/vendas/lote', async (route) => {
    posted = route.request().postDataJSON();
    await route.fulfill({ status: 409, json: { erro: 'Stock alterado. Verifique a quantidade.' } });
  });
  await page.goto('/');
  await navigate(page, 'Vendas');
  await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
  await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Arroz');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.getByLabel(/Qtd/i).fill('1.250');
  await page.getByRole('button', { name: 'Adicionar', exact: true }).click();
  await expect(page.getByText('Total do Carrinho:').locator('..')).toContainText('100');
  await page.getByLabel('Valor (MT)').fill('150');
  await expect(page.locator('.notice-success')).toContainText('Troco a devolver ao cliente: MT 50.00');
  await noOverflow(page);
  await page.screenshot({ path: testInfo.outputPath('carrinho-mobile.png'), fullPage: true });
  await page.getByRole('button', { name: /Confirmar venda/i }).click();
  await expect(page.getByRole('alert')).toContainText('Stock alterado');
  await expect(page.getByRole('dialog').getByText(products[0].nome)).toBeVisible();
  expect((posted as any)?.itens).toEqual([{ produtoId: 1, quantidade: '1.250' }]);
});

test('dados vazios e erro de carregamento são distintos', async ({ page }) => {
  await mockApi(page);
  let fail = true;
  await page.route('**/api/produtos*', (route) =>
    route.fulfill({ status: fail ? 503 : 200, json: fail ? { erro: 'indisponível' } : [] }),
  );
  await page.goto('/');
  await navigate(page, 'Produtos');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'O seu catálogo começa aqui' })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.getByRole('heading', { name: 'O seu catálogo começa aqui' })).toBeVisible();
});

test('gráfico oferece tabela com os mesmos totais e tema persistente', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: /Ver tabela/i }).click();
  const table = page.getByRole('table', { name: 'Vendas dos últimos sete dias em meticais' });
  await expect(table.locator('tbody tr')).toHaveCount(dashboard.vendasPorDia.length);
  await page.getByRole('button', { name: 'Ativar tema escuro' }).click();
  await expect(page.locator('body')).toHaveClass('dark-theme');
  await page.reload();
  await expect(page.locator('body')).toHaveClass('dark-theme');
});
