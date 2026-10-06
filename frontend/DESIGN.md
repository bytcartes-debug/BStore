# Frontend BStore

## Direção visual

Uma área de trabalho para o balcão da loja, não uma página promocional.
A navegação destaca vendas, produtos e stock. O resumo do dia concentra
os valores num único bloco, em vez de repetir cartões decorativos.

- Base: verde `#17634F`, fundo `#F4F7F6`, superfície `#FFFFFF`, texto
  `#203832`, texto secundário `#52665F` e borda `#D8E2DD`.
- Tipografia: Public Sans, com Segoe UI/sans-serif como alternativas locais.
- Espaçamento: 4, 8, 12, 16, 20, 24 e 32 px.
- Tema claro por defeito; tema escuro existente preservado, com preferência
  guardada no navegador.
- Ícones Lucide SVG. Ícones antigos das categorias são interpretados apenas
  na apresentação; não existe migração nem reescrita automática dos dados.
- O gráfico usa uma única série azul, separada das cores de estado. As cores
  `#387DAE` (claro) e `#4097D2` (escuro) foram validadas para as superfícies
  respetivas. Os mesmos valores estão disponíveis em tabela.

```text
Desktop                         Telemóvel
+----------+-----------------+  +-----------------------+
| BStore   | Conta e tema     |  | Menu | Página | Conta |
|          +-----------------+  +-----------------------+
| Vendas   | Título     Ação  |  | Título e ação         |
| Produtos | Pesquisa/filtros|  | Pesquisa e filtros    |
| ...      | Tabela / resumo |  | Registos com labels   |
| Perfil   |                 |  | Ações por registo     |
+----------+-----------------+  +-----------------------+
```

## Componentes e comportamento

- `src/components/UI.tsx`: carregamento, avisos, pesquisa, campos,
  estados vazios, notificações e modais nativos `dialog`.
- `src/utils/useResource.ts`: leitura cancelável, erro, repetição e proteção
  contra respostas antigas; `useMutation` impede pedidos duplicados.
- `src/utils/api.ts`: mantém cookies e contratos da API, acrescenta um limite
  de espera e tratamento de respostas sem expor erros internos do servidor.
- `src/components/Sidebar.tsx`: navegação permanente no desktop e modal
  acessível no telemóvel, com Escape e reposição do foco.
- Os formulários usam validação nativa e mensagens junto aos campos.
  Uma falha do servidor não fecha o formulário nem apaga o seu conteúdo.
- O dashboard é carregado separadamente para não carregar Recharts no login.
- Nome de apresentação e foto continuam locais ao navegador, como no fluxo
  anterior. A interface explica essa limitação em vez de simular persistência
  no servidor.

## Executar e validar

Comandos a executar a partir da raiz do repositório:

```bash
npm --prefix frontend run dev -- --host 127.0.0.1
```

O Vite utiliza a API existente na porta 8080. O proxy usa `changeOrigin: false`
para preservar o cabeçalho Host do frontend, mantendo a verificação de mesma
origem do backend nos pedidos de escrita. Não reescreve nem remove Origin.
A configuração `.claude/launch.json` abre o frontend na porta 5173.

```bash
npm --prefix frontend run build -- --outDir dist
```

O diretório explícito `dist` permite validar o build sem substituir os assets
servidos pelo Java. O comando de build original, sem esse argumento, mantém
inalterado o destino que o projeto já utilizava.

```bash
npm --prefix frontend run lint
```

```bash
npm --prefix frontend run format:check
```

```bash
npm --prefix frontend exec -- playwright install chromium
```

```bash
npm --prefix frontend run test:e2e
```

A suite levanta o Vite na porta 5174 e interceta **todos** os pedidos `/api/`.
Nenhum teste de criação, edição, venda ou remoção chega à base de dados real.
Os dados simulados existem apenas em `tests/`; a aplicação usa a API real.

Os testes cobrem larguras de 320, 375, 600, 768, 900, 1024 e 1440 px;
acessibilidade automatizada com axe nos dois temas; menu, foco e modais;
carregamento/repetição; validação e retenção de formulários; pesquisa/filtros;
carrinho decimal; permissões de apresentação e confirmação de remoções.
Capturas e traces ficam em `test-results/`, excluído do Git.

A suite de Chromium não substitui testes em dispositivos físicos, Safari,
leitores de ecrã ou na câmara e notificações nativas do Android. Esses plugins
foram preservados, mas não exercitados com hardware nesta alteração.
