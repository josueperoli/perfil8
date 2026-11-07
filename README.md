# Perfil 8

Jogo web de adivinhação inspirado em jogos de tabuleiro de pistas. O backend usa Java 21 e Spring Boot; a interface é servida pelo próprio Spring Boot.

## Requisitos

- Java 21 ou superior
- Uma conexão com a internet na primeira compilação, para baixar as dependências Maven

O Maven Wrapper (`./mvnw`) baixa a versão do Maven configurada no projeto quando necessário.

## Executar

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Abra <http://localhost:8080>.

## Publicar na Vercel

O projeto inclui um `Dockerfile` para executar o Spring Boot com Java 21 no runtime
Container da Vercel. Como a interface e a API são servidas pela mesma aplicação,
publique o repositório inteiro usando a raiz do projeto.

No projeto Vercel já existente, selecione **Container** como Framework Preset em
**Settings → Build and Deployment** caso a Vercel não detecte o `Dockerfile`
automaticamente. Depois, faça um novo deploy. O runtime Container está em beta
na Vercel. A variável `PORT` é fornecida pela plataforma; a aplicação já a utiliza.

Por padrão, a aplicação usa cartas de demonstração e não precisa de variáveis de
IA. Para ativar a IA, configure `AI_ENABLED=true`, `AI_API_KEY`, `AI_BASE_URL` e
`AI_MODEL` nas variáveis de ambiente do projeto Vercel; não envie o arquivo `.env`
nem a chave para o Git.

Após o deploy, a página e as rotas `/api/games` devem responder no mesmo domínio.
O estado das partidas fica em memória, então partidas em andamento são perdidas
quando a instância reinicia.

Por padrão, a aplicação usa cartas locais de demonstração e não exige uma chave de API. Com a IA habilitada, as pistas são geradas em português brasileiro. Para ativar a geração e a avaliação de palpites com Groq, edite o arquivo `.env`:

```dotenv
AI_ENABLED=true
AI_API_KEY=sua-chave-groq
AI_BASE_URL=https://api.groq.com/openai/v1
AI_MODEL=openai/gpt-oss-20b
```

`openai/gpt-oss-20b` é o identificador do modelo hospedado pela Groq; a chave e a API continuam sendo da Groq. O plano gratuito tem limites de uso por modelo.
Com a IA habilitada, palpites com grafias aproximadas de nomes próprios, como “Allan Turrin” para “Alan Turin”, podem ser aceitos quando as pistas identificarem a mesma pessoa. Sem IA, são aceitas apenas respostas com grafia equivalente após normalização e respostas alternativas cadastradas.

Para OpenAI, altere o endpoint e o nome do modelo:

```dotenv
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
```

O `.env` é ignorado pelo Git. Nunca coloque a chave no frontend ou no `.env.example`. Com IA habilitada, a aplicação falha na inicialização se `AI_API_KEY` não estiver configurada.

## Testes e pacote

```bash
./mvnw test
./mvnw package
java -jar target/perfil8-0.0.1-SNAPSHOT.jar
```

## API

- `POST /api/games` — cria uma partida (`{"difficulty":"MEDIO","players":["Ana","Bia"],"playerAvatars":["fox","owl"]}`)
- `GET /api/games/{id}` — consulta a partida
- `POST /api/games/{id}/guess` — envia um palpite (`{"guess":"..."}`)
- `POST /api/games/{id}/clue` — revela a próxima pista
- `POST /api/games/{id}/pass-turn` — passa a vez em uma competição
- `POST /api/games/{id}/give-up` — encerra e revela a resposta
- `POST /api/games/{id}/next-round` — inicia a vez do próximo jogador ou outra rodada individual

Uma competição aceita de 2 a 8 participantes. Cada jogador escolhe um avatar predefinido (`fox`, `owl`, `tiger`, `dragon`, `axolotl`, `cat`, `rabbit` ou `octopus`) e recebe uma carta por vez; ao terminar a rodada, o tabuleiro mostra o avanço antes da passagem de vez. Partidas individuais têm no máximo 10 cartas. Cada carta tem 10 pistas e vale até 20 pontos; os pontos da carta também são o número de casas avançadas. O tabuleiro individual tem 200 casas e o de competição, 20. O campo `playerAvatars` é opcional e recebe avatares padrão para clientes antigos; `players` também continua opcional.

O estado é mantido em memória no backend e é perdido quando a aplicação reinicia. Sem IA, o catálogo local contém um conjunto limitado de cartas de demonstração por dificuldade.
