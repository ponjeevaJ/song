CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS artists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(250) NOT NULL UNIQUE,
    image_url TEXT
);
CREATE TABLE IF NOT EXISTS albums (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(300) NOT NULL UNIQUE,
    artwork_url TEXT,
    release_date DATE
);
CREATE TABLE IF NOT EXISTS movies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(300) NOT NULL UNIQUE,
    release_year INTEGER
);
CREATE TABLE IF NOT EXISTS genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS songs (
    id VARCHAR(300) PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    movie_name VARCHAR(300),
    singer VARCHAR(500),
    composer VARCHAR(300),
    album_name VARCHAR(300),
    genre_name VARCHAR(120),
    release_date DATE,
    artwork_url TEXT,
    playback_url TEXT,
    lyrics_url TEXT,
    artist_id BIGINT REFERENCES artists(id),
    album_id BIGINT REFERENCES albums(id),
    movie_id BIGINT REFERENCES movies(id),
    genre_id BIGINT REFERENCES genres(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS songs_release_date_idx ON songs(release_date DESC);
CREATE TABLE IF NOT EXISTS playlists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_daily BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (is_daily OR user_id IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS playlists_user_name_idx ON playlists(user_id, name) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS playlists_daily_name_idx ON playlists(name) WHERE is_daily = true;
CREATE TABLE IF NOT EXISTS playlist_songs (
    playlist_id BIGINT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    song_id VARCHAR(300) NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (playlist_id, song_id)
);
CREATE TABLE IF NOT EXISTS favorites (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    song_id VARCHAR(300) NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, song_id)
);
CREATE TABLE IF NOT EXISTS artist_follows (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    artist_id BIGINT NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, artist_id)
);
CREATE TABLE IF NOT EXISTS listening_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    song_id VARCHAR(300) NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    played_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS listening_history_user_idx ON listening_history(user_id, played_at DESC);
CREATE TABLE IF NOT EXISTS sync_logs (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    songs_added INTEGER NOT NULL DEFAULT 0,
    songs_updated INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

INSERT INTO playlists(user_id, name, description, is_daily)
VALUES (NULL, 'Today''s Tamil Beats', 'Daily refreshed from the connected licensed catalog.', true)
ON CONFLICT (name) WHERE is_daily = true DO NOTHING;
