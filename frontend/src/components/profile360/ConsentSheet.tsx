import React from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Theme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";
import GradientButton from "../ui/GradientButton";

export interface ConsentPoint {
  icon: string;
  title: string;
  body: string;
}

interface Props {
  areaName: string;
  areaIcon?: string | null;
  description?: string | null;
  loading?: boolean;
  onAccept: () => void;
  onDecline: () => void;
  /** Personalización (p. ej. el consentimiento de datos de salud). */
  title?: string;
  lead?: string;
  points?: ConsentPoint[];
  acceptText?: string;
  declineText?: string;
}

const POINTS: ConsentPoint[] = [
  { icon: "📝", title: "Qué guardamos", body: "Solo las respuestas que tú decidas dar en esta área, con su fecha. Todas las preguntas se pueden omitir." },
  { icon: "🔐", title: "Cómo lo guardamos", body: "Cifrado. Lo usamos únicamente para personalizar tu plan y los consejos del asistente; nunca lo vendemos ni lo compartimos." },
  { icon: "↩️", title: "Puedes cambiar de opinión", body: "Revoca el permiso cuando quieras desde Cuenta › Privacidad: al hacerlo borramos todas tus respuestas de esta área." },
  { icon: "⚖️", title: "Tus derechos", body: "Conforme a la Ley 1581 de 2012 (Habeas Data) puedes conocer, actualizar, rectificar y eliminar tus datos, y exportarlos desde tu Cuenta." },
];

/** Consentimiento explícito antes de mostrar las preguntas de un área sensible. */
export default function ConsentSheet({
  areaName,
  areaIcon,
  description,
  loading,
  onAccept,
  onDecline,
  title,
  lead,
  points = POINTS,
  acceptText = "Acepto, continuar",
  declineText = "Ahora no",
}: Props) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={styles.body}>
        <Text style={styles.icon}>{areaIcon ?? "🔒"}</Text>
        <Text style={styles.title}>{title ?? `${areaName} es información privada`}</Text>
        <Text style={styles.lead}>
          {lead ?? `${description ? `${description} ` : ""}Antes de preguntarte, queremos que sepas cómo cuidamos estos datos. Responder es opcional.`}
        </Text>
        <Card style={{ gap: 14, marginTop: 16 }} delay={60}>
          {points.map((p) => (
            <View key={p.title} style={styles.point}>
              <Text style={{ fontSize: 20 }}>{p.icon}</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.pointTitle}>{p.title}</Text>
                <Text style={styles.pointBody}>{p.body}</Text>
              </View>
            </View>
          ))}
        </Card>
      </ScrollView>
      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        <GradientButton title={declineText} variant="ghost" onPress={onDecline} disabled={loading} style={{ flex: 1 }} />
        <GradientButton title={acceptText} onPress={onAccept} loading={loading} style={{ flex: 1.6 }} />
      </View>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  body: { padding: 20, paddingBottom: 24, maxWidth: 640, width: "100%" as const, alignSelf: "center" as const },
  icon: { fontSize: 44, textAlign: "center" as const },
  title: { fontSize: 20, fontWeight: "800" as const, color: colors.text, textAlign: "center" as const, marginTop: 8 },
  lead: { fontSize: 14, color: colors.textMuted, textAlign: "center" as const, lineHeight: 20, marginTop: 8 },
  point: { flexDirection: "row" as const, gap: 12 },
  pointTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  pointBody: { fontSize: 13, color: colors.textMuted, lineHeight: 19, marginTop: 2 },
  footer: { flexDirection: "row" as const, gap: 10, padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
