package com.sportacus.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class KnowledgeRulesTest {

    @Test
    void hashChangesOnlyWhenTextChanges() {
        KnowledgeDoc a = new KnowledgeDoc("guide", "x", null, "Título", "Texto", "Fuente");
        assertThat(a.hash()).isEqualTo(new KnowledgeDoc("guide", "x", null, "Título", "Texto", "Fuente").hash());
        assertThat(a.hash()).isNotEqualTo(new KnowledgeDoc("guide", "x", null, "Título", "Texto nuevo", "Fuente").hash());
        assertThat(a.hash()).isNotEqualTo(new KnowledgeDoc("guide", "x", "gym", "Título", "Texto", "Fuente").hash());
        assertThat(a.embeddingText()).isEqualTo("Título. Texto");
    }

    @Test
    void curatedGuidesAreWellFormed() {
        List<KnowledgeDoc> guides = new KnowledgeCorpus(null, null).guides();
        assertThat(guides).hasSizeGreaterThanOrEqualTo(20);
        Set<String> ids = new HashSet<>();
        Set<String> worlds = Set.of("gym", "calisthenics", "running", "health", "football", "base");
        for (KnowledgeDoc g : guides) {
            assertThat(ids.add(g.sourceId())).as("id repetido: " + g.sourceId()).isTrue();
            assertThat(g.title()).isNotBlank();
            assertThat(g.content()).hasSizeGreaterThan(150);
            assertThat(g.reference()).as("guía sin fuente: " + g.sourceId()).isNotBlank();
            if (g.worldKey() != null) assertThat(worlds).contains(g.worldKey());
        }
    }

    @Test
    void modelNamesMatchWithOrWithoutTag() {
        List<String> installed = List.of("nomic-embed-text:latest", "qwen2.5:3b");
        assertThat(LocalLlmClient.hasModel(installed, "nomic-embed-text")).isTrue();
        assertThat(LocalLlmClient.hasModel(installed, "qwen2.5:3b")).isTrue();
        assertThat(LocalLlmClient.hasModel(installed, "qwen2.5:7b")).isFalse();
    }

    @Test
    void vectorLiteralFormat() {
        assertThat(KnowledgeRetrievalService.toVectorLiteral(new float[] {0.5f, -1f})).isEqualTo("[0.5,-1.0]");
    }
}
