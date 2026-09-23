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
- Render.com — hospedagem da aplicação/API

---

Estrutura do Projecto

bstore/
├── android/                  ← Projecto Android/Capacitor
├── public/                   ← Ficheiros públicos
├── src/
│   ├── components/           ← Componentes da interface
│   ├── pages/                ← Páginas da aplicação
│   ├── services/             ← Comunicação com a API
│   ├── types/                ← Tipos e interfaces TypeScript
│   └── App.tsx               ← Aplicação principal
│
├── server/
│   └── src/main/java/
│       ├── controller/       ← Endpoints/API
│       ├── model/            ← Entidades/modelos
│       ├── service/          ← Regras de negócio
│       ├── repository/       ← Acesso à base de dados
│       └── config/           ← Configurações
│
├── pom.xml                   ← Configuração Maven
├── package.json              ← Dependências do frontend
└── vite.config.ts            ← Configuração Vite

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

- Java 11 ou superior
- Maven
- Node.js e npm
- Android Studio, para gerar a aplicação Android
- PostgreSQL
- Ligação à Internet

Executar o Backend

# Entrar na pasta do servidor
cd server

# Compilar
mvn clean package

# Executar
java -jar target/bstore-server.jar

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
- Utilização através de dispositivos móveisSe isto for para entregar como documentação do Projecto 3 de Engenharia de Software, eu também posso adaptar para ficar com o mesmo nível/formato académico do exemplo do ISCIM, incluindo Requisitos Funcionais, Requisitos Não Funcionais, Casos de Uso, arquitectura, base de dados e descrição de cada módulo.    ├── ui/             ← Interface gráfica Swing
    │   ├── App.java    ← Ponto de entrada
    │   ├── MainFrame.java
    │   ├── Cores.java
    │   └── panels/     ← Painéis da aplicação
    └── util/           ← JPAUtil (gestão EntityManagerFactory)
```

## Como Compilar e Executar

### Pré-requisitos
- Java 11 ou superior instalado
- Maven 3.6 ou superior instalado
- Ligação à internet (para descarregar dependências na primeira vez)

### Passos

```bash
# 1. Entrar na pasta do projecto
cd barraca-projeto3

# 2. Compilar e empacotar (cria um JAR executável com todas as dependências)
mvn package -DskipTests

# 3. Executar
java -jar target/barraca-sistema-1.0-shaded.jar
```

### Alternativa (só compilar)
```bash
mvn compile
mvn exec:java -Dexec.mainClass="ui.App"
```

## Funcionalidades

### Dashboard
- Resumo do dia: total de vendas, número de transacções
- Contador de produtos registados
- Alertas de produtos com stock baixo

### Gestão de Categorias
- Criar, editar e eliminar categorias
- Pesquisa por nome
- Validação: não permite eliminar categoria com produtos associados

### Gestão de Produtos
- Registo completo de produtos (nome, preço, stock, unidade, categoria)
- Definição de stock mínimo para alertas automáticos
- Destaque visual para produtos em alerta (linha amarelada)
- Pesquisa por nome

### Registo de Vendas
- Selecção de produto com cálculo automático do total
- Desconto automático do stock após venda
- Validação de stock antes de confirmar a venda
- Filtro por período de datas
- Relatório com total do período

## Base de Dados

O ficheiro `barraca-db.mv.db` é criado automaticamente na pasta onde
o programa é executado. Os dados persistem entre sessões.
