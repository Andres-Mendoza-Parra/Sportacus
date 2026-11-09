-- Tabla del asistente RAG. pgvector es opcional: si la extensión no está instalada en el servidor
-- (o el usuario no puede crearla) la migración no falla; el chat responde 503 y el resto de la app
-- funciona igual. Para activarlo: instalar pgvector y ejecutar como superusuario
--   CREATE EXTENSION vector SCHEMA sportacus_v2;
-- y luego volver a aplicar este bloque (o reiniciar el backend con flyway repair si hiciera falta).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS vector;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Sin privilegios para crear la extensión vector: se omite el RAG';
        END;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector') THEN
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
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops)';
    ELSE
        RAISE NOTICE 'pgvector no disponible: knowledge_chunk no se crea';
    END IF;
END
$$;
