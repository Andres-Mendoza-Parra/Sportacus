import React from "react";
import { Text, View } from "react-native";

import { humanize } from "../../constants/labels";
import { MovementDetail } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { Card, CardTitle } from "../ui/Card";
import Chip from "../ui/Chip";
import NoticeBox from "../ui/NoticeBox";
import { openSource } from "../assessments/shared";
import { LevelDots } from "./MovementRow";

/** Lista con viñeta o numerada dentro de una tarjeta; no se muestra si está vacía. */
export function ListCard({ title, items, numbered, icon, delay }: { title: string; items: string[]; numbered?: boolean; icon?: string; delay: number }) {
  const styles = useThemedStyles(createStyles);
  if (items.length === 0) return null;
  return (
    <Card delay={delay}>
      <CardTitle title={title} />
      {items.map((text, i) => (
        <View key={i} style={styles.item}>
          <Text style={styles.bullet}>{numbered ? `${i + 1}.` : (icon ?? "•")}</Text>
          <Text style={styles.itemText}>{text}</Text>
        </View>
      ))}
    </Card>
  );
}

/** Descripción, ficha (patrón/nivel/impacto), pasos, claves, errores, músculos y equipo. */
export function MovementBody({ detail }: { detail: MovementDetail }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 12 }}>
      {!detail.allowed ? (
        <NoticeBox tone="danger" title="No recomendado para ti" body="Según tu chequeo de salud, este movimiento puede cargar una zona sensible. Elige una alternativa o consulta a un profesional." />
      ) : null}
      {detail.cautions.map((c, i) => (
        <NoticeBox key={i} tone="warn" body={c} />
      ))}

      <Card delay={0}>
        {detail.description ? <Text style={styles.desc}>{detail.description}</Text> : null}
        <View style={styles.facts}>
          {detail.pattern ? <Fact label="Patrón" value={humanize(detail.pattern)} /> : null}
          {detail.impact ? <Fact label="Impacto" value={humanize(detail.impact)} /> : null}
          {detail.level ? (
            <View style={{ gap: 4 }}>
              <Text style={styles.factLabel}>Nivel</Text>
              <LevelDots level={detail.level} />
            </View>
          ) : null}
        </View>
      </Card>

      <ListCard title="Paso a paso" items={detail.steps} numbered delay={60} />
      <ListCard title="Claves de técnica" items={detail.cues} icon="✅" delay={100} />
      <ListCard title="Errores comunes" items={detail.commonMistakes} icon="❌" delay={140} />

      {detail.muscles.length + detail.equipment.length > 0 ? (
        <Card delay={180}>
          {detail.muscles.length ? (
            <>
              <Text style={styles.factLabel}>Músculos</Text>
              <View style={styles.chips}>
                {detail.muscles.map((m) => (
                  <Chip key={m} label={humanize(m)} />
                ))}
              </View>
            </>
          ) : null}
          <Text style={[styles.factLabel, detail.muscles.length ? { marginTop: 12 } : null]}>Equipo</Text>
          <View style={styles.chips}>
            {detail.equipment.length ? detail.equipment.map((e) => <Chip key={e} label={humanize(e)} />) : <Chip label="Sin equipo" />}
          </View>
        </Card>
      ) : null}
    </View>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 2 }}>
      <Text style={styles.factLabel}>{label}</Text>
      <Text style={styles.factValue}>{value}</Text>
    </View>
  );
}

/** Atribución obligatoria de la fuente del contenido (docs/17). */
export function Attribution({ detail }: { detail: MovementDetail }) {
  const styles = useThemedStyles(createStyles);
  const parts = [
    detail.sourceName ? `Fuente: ${detail.sourceName}` : null,
    detail.license ? `Licencia: ${detail.license}` : null,
    detail.author ? `Autor: ${detail.author}` : null,
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return (
    <Text
      style={[styles.attribution, detail.sourceUrl ? styles.link : null]}
      onPress={detail.sourceUrl ? () => openSource(detail.sourceUrl as string) : undefined}
      accessibilityRole={detail.sourceUrl ? "link" : "text"}
    >
      {parts.join(" · ")}
    </Text>
  );
}

const createStyles = ({ colors }: Theme) => ({
  desc: { fontSize: 14, color: colors.text, lineHeight: 21, marginBottom: 12 },
  facts: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 22 },
  factLabel: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const, letterSpacing: 0.6 },
  factValue: { fontSize: 14, color: colors.text, fontWeight: "600" as const },
  item: { flexDirection: "row" as const, gap: 8, marginBottom: 8, alignItems: "flex-start" as const },
  bullet: { fontSize: 13, color: colors.primary, fontWeight: "700" as const, minWidth: 18 },
  itemText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 },
  chips: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 6, marginTop: 6 },
  attribution: { fontSize: 11, color: colors.textFaint, textAlign: "center" as const, lineHeight: 16 },
  link: { textDecorationLine: "underline" as const },
});
