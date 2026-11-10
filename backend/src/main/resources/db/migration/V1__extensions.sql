-- Extensions required by the data model (docs/03-data-model.md).
--   vector   : question embeddings + HNSW similarity search
--   citext   : case-insensitive unique emails
--   pgcrypto : gen_random_uuid() defaults
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
