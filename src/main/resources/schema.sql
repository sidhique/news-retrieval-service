CREATE TABLE IF NOT EXISTS articles (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    url TEXT,
    publication_date TIMESTAMP,
    source_name TEXT,
    category TEXT[] NOT NULL DEFAULT '{}',
    relevance_score DOUBLE PRECISION,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_point geography(Point, 4326) NOT NULL,
    ai_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_articles_location_point
    ON articles USING GIST (location_point);

CREATE TABLE IF NOT EXISTS trending_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_point geography(Point, 4326),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE trending_events
    ADD COLUMN IF NOT EXISTS location_point geography(Point, 4326);

UPDATE trending_events
SET location_point = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE location_point IS NULL;

CREATE INDEX IF NOT EXISTS idx_trending_events_occurred_at
    ON trending_events (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_trending_events_location_point
    ON trending_events USING GIST (location_point);

