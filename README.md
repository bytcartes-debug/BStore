# Sistema de Gestão de Barraca — Projecto 3
## ISCIM — Engenharia de Software — 3.º Ano ISD, Turma A
### Maio de 2026

---

## Descrição

Sistema completo de gestão de mercado informal (barraca) com interface gráfica,
implementado em Java com Hibernate/JPA e base de dados H2 embutida.

## Tecnologias

- Java 11
- Hibernate 5.6 + JPA 2.2
- H2 Database (embutida, sem instalação)
- Java Swing com FlatLaf (interface moderna)
- Maven (gestão de dependências)

## Estrutura do Projecto

```
barraca-projeto3/
├── pom.xml
└── src/main/java/
    ├── model/          ← Entidades JPA (Categoria, Produto, Venda)
    ├── dao/            ← Camada de acesso a dados (CRUD + queries)
    ├── service/        ← Lógica de negócio (BarracaService)
    ├── ui/             ← Interface gráfica Swing
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
