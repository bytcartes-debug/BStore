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
  await page.getByRole('button', { name: 'Guardar alterações', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(sent[0].icone).toBe(categories[0].icone);
  await page.getByRole('button', { name: 'Editar categoria Mercearia' }).click();
  await page.getByRole('button', { name: 'Fruta', exact: true }).click();
  await page.getByRole('button', { name: 'Guardar alterações', exact: true }).click();
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
    return route.fulfill({ json: { itens: 1, total: '35.00' } });
  });
  await page.goto('/');
  await navigate(page, 'Vendas');
  await page.getByRole('button', { name: 'Registar venda', exact: true }).click();
  await page.getByRole('combobox', { name: 'Pesquisar produto' }).fill('Água');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: 'Adicionar', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar venda', exact: true }).click();
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
    .getByRole('button', { name: 'Confirmar pagamento', exact: true })
    .click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('O servidor não conseguiu');
  expect(count).toBe(1);
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
