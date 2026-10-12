BStore (Flex Stock) — Projecto

Sistema de Gestão de Stock e Vendas para Pequenos Negócios

---

Descrição

O BStore (Flex Stock) é um sistema de gestão de stock e vendas desenvolvido para pequenos estabelecimentos comerciais, como barracas, mercearias e mini-lojas.

O sistema permite gerir produtos, stock, vendas, devedores e utilizadores através de uma aplicação Android, com suporte adicional para acesso através de navegador Web.

A aplicação foi desenvolvida com uma arquitectura cliente-servidor, onde a aplicação Android comunica com uma API REST desenvolvida em Java. Os dados são armazenados numa base de dados PostgreSQL.

O sistema foi pensado para ser simples de utilizar, mesmo por utilizadores com pouca experiência em tecnologia.

---

Tecnologias

- Java — desenvolvimento da API
- Javalin — criação da API REST
- Maven — gestão de dependências
- PostgreSQL — base de dados
- React + TypeScript — interface da aplicação
- Vite — ferramenta de desenvolvimento e build
- Capacitor — transformação da aplicação Web em aplicação Android
- Android — plataforma móvel
- HTML/CSS/JavaScript — interface
- Railway — hospedagem de produção (Dockerfile multi-stage)

---

Estrutura do Projecto

BStore/
├── frontend/                     ← Aplicação Web & Mobile (React, TypeScript, Vite, Tailwind)
│   ├── src/                      ← Código-fonte da interface (páginas, componentes, hooks, utils)
│   ├── tests/                    ← Testes de interface e fluxo Playwright
│   ├── package.json              ← Dependências do frontend
│   └── vite.config.ts            ← Configuração Vite
├── src/main/java/
│   ├── api/                      ← Servidor Javalin, segurança, filtros RLS e helpers (<150 linhas)
│   │   └── routes/               ← Classes de rotas modulares (<300 linhas cada)
│   │       ├── AuthRoutes.java
│   │       ├── CaixaRoutes.java
│   │       ├── CategoriaRoutes.java
│   │       ├── ConfiguracaoRoutes.java
│   │       ├── DashboardRoutes.java
│   │       ├── DevedorRoutes.java
│   │       ├── ProdutoRoutes.java
│   │       └── VendaRoutes.java
│   ├── dao/                      ← Camada de persistência JPA/Hibernate
│   ├── model/                    ← Entidades JPA mapeadas para PostgreSQL
│   ├── service/                  ← Serviços especializados por domínio (<400 linhas cada)
│   │   ├── BarracaService.java   ← Fachada unificada de compatibilidade
│   │   ├── CaixaService.java
│   │   ├── CategoriaService.java
│   │   ├── ConfiguracaoService.java
│   │   ├── DashboardService.java
│   │   ├── DevedorService.java
│   │   ├── DevolucaoService.java
│   │   ├── MetodoPagamentoService.java
│   │   ├── ProcessadorVendaLote.java
│   │   ├── ProdutoService.java
│   │   ├── RateLimiterLogin.java
│   │   ├── RelatorioService.java
│   │   ├── StockService.java
│   │   ├── VendaIdempotenteService.java
│   │   ├── VendaLoteResultado.java
│   │   └── VendaService.java
│   └── util/                     ← Utilitários (Fuso Africa/Maputo, CsvUtil, JPAUtil, EnvUtil)
├── src/main/resources/
│   ├── META-INF/persistence.xml  ← Configuração JPA/Hibernate para PostgreSQL
│   └── db/migration/postgresql/  ← Migrações Flyway versionadas (V1 a V12)
├── src/test/java/                ← Testes de integração (PostgreSQL com Testcontainers) e unidade
├── Dockerfile                    ← Build multi-stage (Node 20 + Maven Java 21 + JRE Eclipse Temurin)
├── railway.json                  ← Configuração de deploy no Railway (healthcheck /api/saude)
├── .gitattributes                ← Normalização eol=lf para repositório limpo
└── pom.xml                       ← Configuração e dependências Maven do backend

---

Arquitectura do Sistema

┌──────────────────────────┐
│     Android / Browser    │
│    React + Capacitor     │
└────────────┬─────────────┘
             │
             │ HTTP / REST API
             ▼
┌──────────────────────────┐
│      BStore Server       │
│     Java + Javalin       │
└────────────┬─────────────┘
             │
             │ JDBC / SQL
             ▼
┌──────────────────────────┐
│       PostgreSQL         │
│        Database          │
└──────────────────────────┘

---

Como Compilar e Executar

Pré-requisitos

- Java 17 ou 21
- Maven 3.8+
- Node.js 20 e npm
- Docker Desktop (opcional para testes Testcontainers e PostgreSQL local)
- PostgreSQL 15+ ou Supabase
- Fuso horário oficial: Africa/Maputo (UTC+2) em todas as camadas

Configuração de Ambiente Local (.env)

Crie ou configure o seu ficheiro .env na raiz (nunca comitar este ficheiro):

APP_TIMEZONE=Africa/Maputo
DATABASE_URL=postgresql://localhost:5432/bstore
DB_USER=bstore_app
DB_PASSWORD=sua_senha
MIGRATION_DB_URL=postgresql://localhost:5432/bstore
MIGRATION_DB_USER=postgres
MIGRATION_DB_PASSWORD=sua_senha_admin
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=2
SESSION_COOKIE_SECURE=false
CORS_ALLOWED_ORIGIN=http://localhost:5173

Executar com PostgreSQL local via Docker (opcional)

docker run --name bstore-postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=bstore -p 5432:5432 -d postgres:16-alpine

Executar o Backend em Desenvolvimento

mvn clean compile exec:java -Dexec.mainClass="api.Main"

Ou compilar e rodar o pacote jar:

mvn clean package -DskipTests
java -Duser.timezone=Africa/Maputo -jar target/barraca-sistema-1.0.jar

Como Correr os Testes

Pré-requisitos dos testes:
- Docker Desktop em execução (para o Testcontainers provisionar o PostgreSQL 16 automaticamente).
- Caso o Docker não esteja disponível no ambiente, os testes de integração avisam e são ignorados com segurança (Assumptions do JUnit 5), sem nunca recorrer a bases em memória legadas (H2 eliminado).

Comando de testes do backend:
mvn test

Comando de testes do frontend:
cd frontend
npx oxlint
npm run build
npx playwright test tests/dataHora.spec.ts tests/vendasEspera.spec.ts

Executar o Frontend

# Instalar dependências
npm install

# Iniciar em modo desenvolvimento
npm run dev

Gerar a aplicação Android

# Criar o build Web
npm run build

# Sincronizar com o Android
npx cap sync android

# Abrir no Android Studio
npx cap open android

---

Funcionalidades

Dashboard

- Resumo das vendas
- Total vendido
- Número de vendas realizadas
- Quantidade de produtos registados
- Produtos com stock baixo
- Resumo de dívidas
- Informações importantes do negócio

---

Gestão de Produtos

Permite:

- Registar produtos
- Editar produtos
- Eliminar produtos
- Pesquisar produtos
- Consultar stock
- Definir preço de venda
- Definir quantidade em stock
- Definir stock mínimo
- Associar código de barras
- Identificar produtos através do código de barras

Código de barras

Durante o registo de um produto, o utilizador pode efectuar a leitura do código de barras.

Quando o código corresponde a um produto disponível no Open Food Facts, os dados disponíveis podem ser utilizados para preencher automaticamente os campos do produto.

O utilizador pode verificar e editar os dados antes de confirmar o registo.

Para produtos locais ou que não estejam disponíveis no Open Food Facts, o nome e os restantes dados podem ser preenchidos manualmente.

---

Gestão de Stock

O sistema permite acompanhar automaticamente a quantidade disponível de cada produto.

Quando uma venda é confirmada:

Stock actual - Quantidade vendida = Novo stock

Exemplo:

Stock: 20
Venda: 3

20 - 3 = 17

Novo stock: 17

O sistema também pode apresentar alertas quando o stock atingir ou ficar abaixo do stock mínimo definido.

---

Registo de Vendas

Permite:

- Seleccionar produtos
- Pesquisar produtos
- Ler códigos de barras
- Definir quantidade
- Calcular automaticamente o subtotal
- Calcular o total da venda
- Confirmar a venda
- Actualizar automaticamente o stock

Exemplo:

Produto: Coca-Cola
Preço: 80 MZN
Quantidade: 3

80 × 3 = 240 MZN

Ao confirmar a venda, o stock do produto é reduzido automaticamente.

---

Venda através de Código de Barras

Durante uma venda, o utilizador pode utilizar o leitor de código de barras.

O sistema procura o código na base de dados local:

Código de barras
       ↓
Base de dados BStore
       ↓
Produto encontrado?
    ↙       ↘
  SIM       NÃO
   ↓          ↓
Adicionar   Informar que
produto     produto não
à venda     está registado

Na venda, o sistema utiliza a base de dados local para evitar depender de uma consulta externa durante o processo de venda.

---

Gestão de Devedores

O sistema permite controlar vendas feitas a crédito.

Funcionalidades:

- Registar devedores
- Associar vendas a um devedor
- Consultar valor em dívida
- Consultar histórico
- Registar pagamento
- Actualizar automaticamente o valor da dívida

Quando uma dívida é paga:

Dívida: 500 MZN
Pagamento: 500 MZN

Saldo da dívida = 0 MZN

---

Gestão de Utilizadores

O BStore permite a utilização do sistema por diferentes utilizadores, mantendo os dados de cada negócio separados.

Cada utilizador pode ter acesso apenas aos dados pertencentes à sua conta/estabelecimento.

A autenticação permite controlar o acesso às funcionalidades do sistema.

---

Pesquisa

O sistema permite pesquisar rapidamente:

- Produtos
- Códigos de barras
- Devedores
- Vendas
- Outros registos necessários

A pesquisa foi pensada para permitir que o utilizador encontre rapidamente a informação necessária sem navegar por várias telas.

---

Base de Dados

O BStore utiliza PostgreSQL como sistema de gestão de base de dados.

Os principais dados armazenados incluem:

Utilizadores
     │
     ├── Produtos
     │      ├── Código de barras
     │      ├── Nome
     │      ├── Preço
     │      ├── Stock
     │      └── Stock mínimo
     │
     ├── Vendas
     │      ├── Produtos
     │      ├── Quantidades
     │      ├── Total
     │      └── Data
     │
     └── Devedores
            ├── Nome
            ├── Dívida
            └── Pagamentos

Os dados são armazenados no servidor e permanecem disponíveis entre diferentes sessões de utilização da aplicação.

---

Hospedagem

O backend do BStore é hospedado num servidor Render.com, disponibilizando a API utilizada pela aplicação.

A comunicação entre o cliente e o servidor é realizada através de requisições HTTP à API REST.

Exemplo:

Aplicação BStore
       ↓
HTTP Request
       ↓
BStore API
       ↓
PostgreSQL
       ↓
HTTP Response
       ↓
Aplicação BStore

---

Objectivo do Sistema

O principal objectivo do BStore é fornecer uma solução simples e acessível para a gestão de pequenos negócios, permitindo substituir processos manuais de controlo de stock e vendas por um sistema digital.

O sistema procura facilitar principalmente:

- Controlo de stock
- Registo de vendas
- Gestão de produtos
- Controlo de dívidas
- Identificação de produtos por código de barras
- Consulta das informações do negócio
---

Deploy no Railway
=================

O BStore utiliza o Dockerfile multi-stage na raiz do repositório como fonte única de verdade para construção e arranque da imagem em produção.

1. Configuração do Serviço no Railway
- Crie um novo projecto ou serviço apontando para o repositório GitHub.
- O Railway detecta automaticamente o railway.json com builder DOCKERFILE.
- O healthcheck está configurado para o endpoint /api/saude com timeout de 30 segundos.

2. Variáveis de Ambiente Obrigatórias
No painel do Railway (Settings → Variables), configure as seguintes variáveis (nunca comitar valores reais):

- MIGRATION_DB_URL: URL PostgreSQL directa (porta 5432) usada pelo Flyway para aplicar migrações estruturais.
  Exemplo: postgresql://postgres:SENHA@db.SEU_REF.supabase.co:5432/postgres
- MIGRATION_DB_USER: utilizador dono da base de dados (ex: postgres).
- MIGRATION_DB_PASSWORD: senha do utilizador postgres.

- DATABASE_URL: URL do Transaction Pooler do Supabase (porta 6543) com o papel restrito bstore_app.
  Exemplo: postgresql://bstore_app:SENHA@SEU_POOLER_HOST:6543/postgres?sslmode=require&prepareThreshold=0
- DB_USER: bstore_app
- DB_PASSWORD: senha do papel restrito bstore_app.

- DB_POOL_MAX_SIZE: tamanho máximo do pool HikariCP (recomendado: 5 a 10 para respeitar limites do Supabase).
- DB_POOL_MIN_IDLE: conexões mínimas em espera (recomendado: 2).
- APP_TIMEZONE: Africa/Maputo
- PORT: porta injectada pelo Railway (por defeito 8080).
- SESSION_COOKIE_SECURE: true (para forçar o envio de cookies de sessão apenas por HTTPS).

3. Compatibilidade com Transaction Pooler (PgBouncer)
- O parâmetro prepareThreshold=0 é configurado na ligação para evitar conflitos de prepared statements no pooler do Supabase na porta 6543 (transaction mode).
- As definições de utilizador corrente para Row Level Security (RLS) utilizam set_config('app.current_user_id', ..., true), garantindo escopo estrito à transacção corrente sem vazamento entre clientes do pool.

4. Papel Restrito bstore_app e Isolamento com Row Level Security (RLS)
- O BStore opera sob o princípio do menor privilégio: a aplicação em tempo de execução conecta-se exclusivamente com o utilizador `bstore_app`.
- O utilizador `bstore_app` NÃO possui permissões de DDL (não cria nem altera tabelas) e não faz bypass de RLS.
- As migrações de schema (DDL) são executadas no arranque pela conexão isolada de migração (porta direta 5432) com privilégios de administrador (ex: postgres).
- Todas as tabelas sensíveis de dados possuem Row Level Security (RLS) activo e políticas que garantem que cada loja/utilizador apenas lê e grava os seus próprios dados:
  `usuario_id = NULLIF(current_setting('app.current_user_id', true), '')::bigint`
- A migração `V12__rls_papel_restrito_e_seguranca.sql` garante a criação condicional do papel `bstore_app`, atribuição de permissões CRUD nas tabelas e sequências, e aplicação das políticas de RLS.

5. Procedimento de Rollback
- Se uma versão apresentar anomalias após deploy:
  a) No painel do Railway, aceda a "Deployments".
  b) Localize o deployment anterior estável.
  c) Clique nos três pontos (...) e seleccione "Rollback to this deployment".
- Caso a reversão envolva migrações de base de dados, execute os scripts de reversão correspondentes na consola SQL do Supabase.
