# DeOlho

Plataforma de monitoramento de erros com processamento assíncrono e Inteligência Artificial para identificar, agrupar, analisar e sugerir soluções para exceções em aplicações Java.

## Stack

- **Java 21** (Virtual Threads)
- **Spring Boot 3.4**
- **Spring Data JPA** + SQLite
- **Spring AI** (OpenAI)
- **Custom Queue System** (BlockingQueue + Virtual Threads)

## Início rápido

```bash
# Compilar
./mvnw clean compile

# Rodar
./mvnw spring-boot:run

# Testar
./mvnw test
```

## Enviando um erro

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "application": "payment-service",
    "level": "ERROR",
    "message": "Connection pool exhausted",
    "exception": "java.sql.SQLTransientConnectionException",
    "stacktrace": "at com.zaxxer.hikari.pool.HikariPool.createTimeoutException...",
    "host": "server-01",
    "environment": "production"
  }'
```

## Consultando erros

```bash
# Listar todos
curl http://localhost:8080/api/v1/errors

# Buscar
curl http://localhost:8080/api/v1/errors/search?q=connection

# Top erros
curl http://localhost:8080/api/v1/errors/top

# Estatísticas
curl http://localhost:8080/api/v1/stats/overview
```

## API Endpoints

### Eventos
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/events` | Enviar erro |
| POST | `/api/v1/events/batch` | Enviar batch de erros |

### Erros
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/errors` | Listar erros (paginado) |
| GET | `/api/v1/errors/{id}` | Detalhe do erro |
| GET | `/api/v1/errors/{id}/analysis` | Análise IA do erro |
| PATCH | `/api/v1/errors/{id}/status` | Atualizar status |
| GET | `/api/v1/errors/search?q=` | Buscar por mensagem/exceção |
| GET | `/api/v1/errors/top` | Ranking de erros frequentes |

### Aplicações
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/applications` | Listar aplicações |
| POST | `/api/v1/applications` | Registrar aplicação |
| GET | `/api/v1/applications/{id}` | Detalhe da aplicação |
| GET | `/api/v1/applications/{id}/errors` | Erros da aplicação |
| DELETE | `/api/v1/applications/{id}` | Remover aplicação |

### Estatísticas
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/stats/overview` | Visão geral |
| GET | `/api/v1/stats/top-errors` | Top erros |
| GET | `/api/v1/stats/timeline` | Timeline por hora |

## Variáveis de ambiente (Seed Inicial)

Estas variáveis são utilizadas apenas na primeira inicialização para pré-configurar o banco de dados.

| Variável | Descrição | Padrão |
|----------|-----------|--------|
| `OPENAI_API_KEY` | Chave da API OpenAI | - |
| `AI_ENABLED` | Habilitar análise por IA | `true` |
| `NOTIFICATIONS_ENABLED` | Habilitar notificações | `false` |
| `WEBHOOK_URL` | URL do webhook de notificação | - |

## Configurações Dinâmicas (Banco de Dados / Painel Web)

Após a inicialização do banco, todas as configurações são dinâmicas e podem ser atualizadas diretamente no painel web ou através da API `/api/v1/settings`.

| Chave | Descrição | Padrão |
|-------|-----------|--------|
| `server.port` | Porta TCP na qual o painel e a API do DeOlho escutam (requer reiniciar a aplicação ao mudar) | `8888` |
| `ai.enabled` | Ativa/desativa a análise automatizada por inteligência artificial | `true` |
| `ai.provider` | Provedor de inteligência artificial (`OPENAI` \| `HEURISTIC`) | `OPENAI` |
| `ai.api-key` | Chave de autenticação (API Key) para o provedor de IA | - |
| `ai.base-url` | URL do endpoint da API (ex: `https://api.openai.com` ou `http://localhost:11434/v1`) | `https://api.openai.com` |
| `ai.model` | Modelo de chat selecionado (ex: `gpt-4o-mini`, `llama3`, `mistral`) | `gpt-4o-mini` |
| `ai.language` | Idioma de retorno das análises de IA (ex: `pt-BR`, `en-US`, `es-ES`, `fr-FR`) | `pt-BR` |


## Arquitetura

```
App → POST /events → Queue → PersistWorker → DB
                                  ↓
                            AiWorker → OpenAI → AI Analysis
                                  ↓
                          NotificationWorker → Webhook
                                  ↓
                            MetricsWorker → Stats
```

## Integração com outros projetos (Cliente)

Para monitorar seus outros projetos automaticamente com o DeOlho, você pode importar a biblioteca leve de integração `deolho-client` que já está instalada localmente no seu repositório Maven local.

### 1. Adicionando a dependência

#### Maven (pom.xml)
```xml
<dependency>
    <groupId>com.deolho</groupId>
    <artifactId>deolho-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

#### Gradle (build.gradle - Groovy)
```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.deolho:deolho-client:0.1.0-SNAPSHOT'
}
```

#### Gradle (build.gradle.kts - Kotlin)
```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.deolho:deolho-client:0.1.0-SNAPSHOT")
}
```

### 2. Configurando no seu projeto

#### Opção A: Monitoramento automático via Spring Boot (Exceções não tratadas)
Se o seu projeto for uma aplicação Spring Boot, a biblioteca intercepta automaticamente qualquer exceção não tratada nos Controllers e encaminha para o DeOlho.

Adicione as configurações no `application.yml`:
```yaml
deolho:
  application-name: nome-da-sua-aplicacao
  environment: development
  server-url: http://localhost:8888/api/v1/events # Opcional (já é o padrão)
```

#### Opção B: Integração via logs do Logback (Captura de erros logados)
Para enviar automaticamente qualquer erro gerado em logger (`log.error("Mensagem", exception)`), configure o Appender no seu arquivo `logback-spring.xml` ou `logback.xml`:

```xml
<configuration>
    <appender name="DEOLHO" class="com.deolho.client.DeOlhoLogbackAppender">
        <serverUrl>http://localhost:8888/api/v1/events</serverUrl>
        <application>nome-da-sua-aplicacao</application>
        <environment>development</environment>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="DEOLHO" />
    </root>
</configuration>
```

## Executando com Docker (Para iniciar junto com a máquina)

O projeto possui um arquivo `docker-compose.yml` configurado com a política de reinicialização automática (`restart: unless-stopped`). Isso faz com que o container do DeOlho seja iniciado de forma automática sempre que o Docker daemon/sistema operacional for carregado.

### Como rodar:

1. Acesse o diretório docker:
   ```bash
   cd docker
   ```
2. Inicie o container em segundo plano (background):
   ```bash
   docker compose up -d
   ```
3. Para parar o container:
   ```bash
   docker compose down
   ```

O container mapeará a porta `8888:8888` e persistirá o banco de dados SQLite (`deolho.db`) em um volume Docker gerenciado (`deolho-data`), garantindo que nenhum dado seja perdido ao reiniciar ou recriar o container.

## Licença

MIT
