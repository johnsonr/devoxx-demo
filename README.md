# Embabel Devoxx demo

Three Embabel Agent features over one corpus: the five Sherlock Holmes short-story collections, 56 stories, from Project Gutenberg.

| Feature | Where | Try it in the shell |
|---|---|---|
| Agentic RAG with `ToolishRag` over an in-memory Lucene store | `RagConfiguration`, `HolmesChat` | `chat` then ask "What was the real purpose of the Red-Headed League?" |
| GOAP planning: evidence -> deduction -> case file | `CaseAgent` | `investigate "Why did Holmes refuse the emerald ring?"` |
| Agent Skills with a Python script in a Docker sandbox | `SkillsConfiguration`, `skills/holmes-stats` | `chat` then ask "How many times is Lestrade mentioned in each story?" |

## Run

Requires Java 21+, an `OPENAI_API_KEY` (for the LLM and embeddings) and, for the skills demo, Docker
with the sandbox image:

```bash
docker build -t embabel/agent-sandbox:latest <embabel-agent repo>/embabel-agent-skills/docker
mvn spring-boot:run
```

The corpus is ingested on first start (a few minutes for embeddings) into a Lucene index at
`data/index`. Later starts reuse the index and skip ingestion. Delete the directory to re-ingest.

Useful shell commands: `chat`, `investigate`, `corpus`, `help`.

## Tests

```bash
mvn test
```

The tests run without an API key or Docker: ingestion and BM25 search against in-memory Lucene,
the agent's actions against `FakeOperationContext`, and skill loading.
