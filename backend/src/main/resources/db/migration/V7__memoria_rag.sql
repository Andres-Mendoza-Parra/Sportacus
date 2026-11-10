-- Memoria del asistente RAG (docs/24). V2 la dejaba opcional y en el Postgres nativo nunca se creó porque no tenía
-- pgvector; el contenedor pgvector/pgvector ya trae la extensión (creada en sportacus_v2 por docker/postgres/init).
-- Sigue siendo tolerante: sin pgvector no falla, el chat responde sin catálogo y el resto de la app funciona igual.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector')
       AND EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS vector;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Sin privilegios para crear la extensión vector: se omite la memoria del asistente';
        END;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector') THEN
        RAISE NOTICE 'pgvector no disponible: knowledge_chunk no se crea';
        RETURN;
    END IF;

    EXECUTE $ddl$
        CREATE TABLE IF NOT EXISTS knowledge_chunk (
            id          BIGSERIAL PRIMARY KEY,
            source_type VARCHAR(40) NOT NULL,
            source_id   VARCHAR(80) NOT NULL,
            world_key   VARCHAR(30),
            content     TEXT NOT NULL,
            metadata    JSONB NOT NULL DEFAULT '{}',
            embedding   vector(768) NOT NULL,
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            UNIQUE (source_type, source_id)
        )
    $ddl$;
    -- Título visible (para citar la fuente en el chat), huella del texto (para reindexar solo lo que cambió)
    -- y modelo con que se generó el vector (si cambia el modelo de embeddings, se rehace todo).
    EXECUTE 'ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS title VARCHAR(160)';
    EXECUTE 'ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64)';
    EXECUTE 'ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(80)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops)';
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_world ON knowledge_chunk (world_key)';
END
$$;
