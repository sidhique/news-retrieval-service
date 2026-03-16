# News Retrieval API

Spring Boot service for ingesting, searching, and ranking news articles with geospatial support, LLM-assisted query interpretation, and a location-based trending feed.

## What this service does

- Stores news articles with metadata, relevance score, and geo coordinates.
- Supports full-text + filter-based search (`source`, `category`, `text`, `nearby`) from extracted query criteria.
- Exposes paginated APIs with consistent `offset`, `limit`, and `count`.
- Builds a "Trending Near Me" feed from user interaction events (`VIEW`/`CLICK`) using PostGIS distance and recency weighting.
- Uses cache-by-location buckets (TTL = 60s) for trending responses.

## API design summary

- **Versioned APIs**: main APIs are under `/api/v1`.
- **Layering**:
  - `api.controllers`: REST controllers
  - `api.dto`: request/response DTOs
  - `article.service`: business logic
  - `article.repository`: SQL/JPA access
  - `article.entity`: persistence entities
  - `article.model`: internal service models
- **Search strategy**:
  - OpenAI extracts structured search criteria (`intents`, `search_term`, `source`, `category`).
  - Search applies multiple intents as `AND` conditions.
  - If `text` intent exists, `search_term` is used for full-text query.
- **Trending strategy**:
  - Events are stored in `trending_events`.
  - Query computes `trending_score` from event type weight, recency decay, and distance factor.
  - Results are ordered by computed score and then freshness.

## Tech stack

- Java 17
- Spring Boot 3.3.x
- Spring Web + Spring Data JPA
- PostgreSQL + PostGIS
- OpenAI Java SDK

## Deployment and infrastructure

- Database: PostgreSQL with PostGIS extension, hosted on Google Cloud Platform (GCP).
- Runtime: Spring Boot application deployed on Google Cloud Run.

## Data model (high level)

### `articles`

- `id` (UUID, PK)
- `title`, `description`, `url`
- `publication_date`, `source_name`, `category[]`
- `relevance_score`
- `latitude`, `longitude`, `location_point` (`geography(Point, 4326)`)
- `ai_summary`
- `created_at`, `updated_at`

### `trending_events`

- `id` (UUID, PK)
- `user_id`, `article_id` (FK to `articles`)
- `event_type` (`VIEW`/`CLICK`)
- `latitude`, `longitude`, `location_point`
- `occurred_at`, `created_at`

## Configuration

From `src/main/resources/application.properties`:

- `app.pagination.default-limit=20`
- `app.pagination.max-limit=100`
- `app.trending.radius-km=50`
- `app.trending.cache-grid-size-degrees=0.25`
- `app.trending.cache-ttl-seconds=60`
- `app.trending.event-retention-minutes=180`

Required env vars for LLM integrations:

- `OPENAI_API_KEY`
- `GEMINI_API_KEY`

## Running locally

1. Start PostgreSQL and ensure PostGIS is enabled.
2. Create DB (default `news_db`) and user matching `application.properties` or set datasource env vars.
3. Run:

```bash
mvn spring-boot:run
```

Base URL: `https://news-retrieval-kcxlpvvfyq-uc.a.run.app`

## Pagination behavior

- Query params: `offset` and `limit`
- Default `limit`: `20`
- Maximum `limit`: `100` (values above 100 are capped)
- Response includes:
  - `pagination.offset`
  - `pagination.limit`
  - `pagination.count` (total matching records, not page size)

## Sample API queries

### Health

```bash
curl -s https://news-retrieval-kcxlpvvfyq-uc.a.run.app/health
```

### Upsert single article (`POST /api/v1/articles`)

If `ai_summary` is provided, Gemini summarization is skipped.

```bash
curl -s -X POST https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/articles \
  -H "Content-Type: application/json" \
  -d '{
    "id": "492a3461-a3a2-4727-9f00-5b04558ddc97",
    "title": "City Transit Expansion Approved",
    "description": "New metro lines approved for development.",
    "url": "https://example.com/news/transit-expansion",
    "publication_date": "2026-03-15T09:30:00",
    "source_name": "Reuters",
    "category": ["world", "infrastructure"],
    "relevance_score": 0.88,
    "ai_summary": "Metro expansion approved with phased rollout.",
    "latitude": 16.120734,
    "longitude": 79.163399
  }'
```

### Upsert articles batch (`POST /api/v1/articles/batch`)

```bash
curl -s -X POST https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/articles/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "title": "Tech Hiring Rebounds",
      "description": "Several firms resumed hiring.",
      "url": "https://example.com/news/tech-hiring",
      "publication_date": "2026-03-15T10:00:00",
      "source_name": "The Verge",
      "category": ["technology"],
      "relevance_score": 0.82,
      "latitude": 16.120734,
      "longitude": 79.163399
    },
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "title": "Cricket Final Draws Huge Crowd",
      "description": "Regional final had record attendance.",
      "url": "https://example.com/news/cricket-final",
      "publication_date": "2026-03-15T10:15:00",
      "source_name": "ESPN",
      "category": ["sports"],
      "relevance_score": 0.79,
      "ai_summary": "Record crowd turnout at regional final.",
      "latitude": 16.120734,
      "longitude": 79.163399
    }
  ]'
```

### Filter by category (`GET /api/v1/news/category`)

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/category?category=technology&offset=0&limit=20"
```

### Filter by relevance score (`GET /api/v1/news/score`)

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/score?threshold=0.7&offset=0&limit=20"
```

### Filter by source (`GET /api/v1/news/source`)

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/source?source=News18&offset=0&limit=20"
```

### Nearby articles (`GET /api/v1/news/nearby`)

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/nearby?latitude=16.120734&longitude=79.163399&radiusKm=25&offset=0&limit=20"
```

### Smart search (`GET /api/v1/news/search`)

Uses OpenAI-extracted criteria and returns them in `search_criteria`.

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/search?query=Ukraine war news"
```

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/search?query=tech news"
```

With location:

```bash
curl --location 'https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/news/search?query=technology%20news%20from%20Moneycontrol%20near%20Hyderabad&location=Hyderabad&radiusKm=200&offset=0&limit=20'
```

## Trending APIs

### Record trending event (`POST /api/v1/events`)

```bash
curl -s -X POST https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "7e1b5d6f-16cf-4ef2-90e3-8465f2f95e5f",
    "article_id": "492a3461-a3a2-4727-9f00-5b04558ddc97",
    "event_type": "CLICK",
    "latitude": 16.120734,
    "longitude": 79.163399,
    "occurred_at": "2026-03-15T16:20:00Z"
  }'
```

### Get trending feed (`GET /api/v1/trending`)

```bash
curl -s "https://news-retrieval-kcxlpvvfyq-uc.a.run.app/api/v1/trending?lat=16.120734&lon=79.163399&limit=20"
```

## Notes

- Missing required query params return a structured `400` with `{ "error": "<param> is required." }`.
- `location` in `/api/v1/news/search` is optional, but required when nearby intent is inferred.
- `event_type` for `/api/v1/events` must be one of `VIEW` or `CLICK` (case-insensitive input accepted).

