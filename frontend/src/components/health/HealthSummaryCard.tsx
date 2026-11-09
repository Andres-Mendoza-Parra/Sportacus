import React from "react";
import { Text, View } from "react-native";

import { RISK_LABELS, formatDate, humanize } from "../../constants/labels";
import { HealthSummary } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";
import GradientButton from "../ui/GradientButton";
import NoticeBox, { NoticeKind } from "../ui/NoticeBox";

interface Props {
  summary: HealthSummary;
  /** Muestra el botón para confirmar el aval profesional cuando hace falta. */
  onConfirmClearance?: () => void;
  confirming?: boolean;
  /** Versión resumida (Cuenta): riesgo, condiciones y aviso, sin detalles extra. */
  compact?: boolean;
}

const RISK_TONE: Record<string, NoticeKind> = { bajo: "success", moderado: "warn", alto: "danger" };
const RISK_ICON: Record<string, string> = { bajo: "🟢", moderado: "🟡", alto: "🔴" };

const FLAG_LABELS: Record<string, string> = {
  inactivo: "Poco activo en los últimos meses",
  sintomas: "Síntomas al hacer esfuerzo",
  enfermedad: "Condición diagnosticada",
  banderas_rojas: "Señales de alerta",
  cirugia_reciente: "Cirugía reciente",
  embarazo: "Embarazo o posparto",
};

/** Resultado del chequeo de salud: nivel de riesgo, condiciones activas y avisos de aval/banderas rojas. */
export default function HealthSummaryCard({ summary, onConfirmClearance, confirming, compact }: Props) {
  const styles = useThemedStyles(createStyles);
  const risk = summary.riskLevel;
  const needsClearance = summary.requiresClearance && !summary.clearanceConfirmed;

  if (!summary.screened) {
    return (
      <Card>
        <Text style={styles.title}>🩺 Chequeo de salud pendiente</Text>
        <Text style={styles.muted}>Haz el chequeo para que cuidemos tus zonas sensibles y ajustemos la intensidad.</Text>
      </Card>
    );
  }

  return (
    <Card style={{ gap: 12 }}>
      <View style={styles.riskRow}>
        <Text style={{ fontSize: compact ? 26 : 34 }}>{RISK_ICON[risk] ?? "⚪"}</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>{RISK_LABELS[risk] ?? humanize(risk)}</Text>
          {summary.screenedAt ? <Text style={styles.muted}>{`Chequeo del ${formatDate(summary.screenedAt, true)}`}</Text> : null}
        </View>
      </View>

      {summary.redFlags ? (
        <NoticeBox
          tone="danger"
          title="Consulta a un profesional antes de entrenar"
          body="Marcaste señales que un médico o fisioterapeuta debe revisar. Por tu seguridad, pausamos la actividad intensa hasta que tengas su valoración."
        />
      ) : null}

      {needsClearance ? (
        <NoticeBox
          tone="warn"
          title="Necesitamos el visto bueno de un profesional"
          body="Por lo que nos contaste, lo más seguro es que un profesional de la salud te autorice antes de empezar. Cuando lo tengas, confírmalo aquí."
        >
          {onConfirmClearance ? (
            <GradientButton title="Confirmo que un profesional me autorizó" onPress={onConfirmClearance} loading={confirming} style={{ marginTop: 10 }} />
          ) : null}
        </NoticeBox>
      ) : null}

      {summary.requiresClearance && summary.clearanceConfirmed ? (
        <NoticeBox tone="success" body="Confirmaste el aval de tu profesional de la salud." />
      ) : null}

      {!compact && summary.flags.length > 0 ? (
        <View style={styles.chips}>
          {summary.flags.map((f) => (
            <View key={f} style={styles.chip}>
              <Text style={styles.chipText}>{FLAG_LABELS[f] ?? humanize(f)}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {summary.conditions.length > 0 ? (
        <View style={{ gap: 6 }}>
          <Text style={styles.label}>Zonas y condiciones que cuidamos</Text>
          {summary.conditions.map((c) => (
            <Text key={c.tagKey} style={styles.item}>
              {`• ${c.label}${c.severity ? ` · ${humanize(c.severity)}` : ""}`}
            </Text>
          ))}
        </View>
      ) : (
        <Text style={styles.muted}>Sin lesiones ni condiciones activas registradas.</Text>
      )}

      {!compact && !summary.redFlags && !needsClearance ? (
        <NoticeBox tone={RISK_TONE[risk] ?? "info"} body="Ajustamos tus planes a este resultado y excluimos los ejercicios que no te convienen." />
      ) : null}
    </Card>
  );
}

const createStyles = ({ colors }: Theme) => ({
  riskRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  title: { fontSize: 16, fontWeight: "700" as const, color: colors.text },
  muted: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
  label: { fontSize: 12, fontWeight: "700" as const, color: colors.textMuted },
  item: { fontSize: 13, color: colors.text },
  chips: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 6 },
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, backgroundColor: colors.cardAlt },
  chipText: { fontSize: 11, color: colors.textMuted, fontWeight: "600" as const },
});
