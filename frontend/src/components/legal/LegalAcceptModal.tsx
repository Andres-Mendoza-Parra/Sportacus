import React, { useState } from "react";
import { Modal, ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { PendingLegal, acceptLegal, fetchLegalDocuments } from "../../services/legalService";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import CheckRow from "../ui/CheckRow";
import GradientButton from "../ui/GradientButton";
import PressableScale from "../ui/PressableScale";
import LegalDocumentView from "./LegalDocumentView";

const NAMES: Record<PendingLegal["document"], string> = {
  politica_datos: "Política de tratamiento de datos",
  terminos: "Términos y condiciones",
};

interface Props {
  pending: PendingLegal[];
  onAccepted: () => void;
  onSignOut: () => void;
}

/**
 * Re-aceptación bloqueante: aparece cuando la versión vigente de la Política o los Términos no es la
 * que la persona aceptó (p. ej. cuentas creadas antes de publicarlos). Los textos se leen aquí mismo.
 */
export default function LegalAcceptModal({ pending, onAccepted, onSignOut }: Props) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const docs = useLoader(fetchLegalDocuments);
  const [open, setOpen] = useState<string | null>(null);
  const [checked, setChecked] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function accept() {
    if (!checked) {
      setError("Marca la casilla para continuar.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      for (const p of pending) await acceptLegal(p.document, p.version);
      onAccepted();
    } catch (e) {
      setError(extractErrorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  const names = pending.map((p) => NAMES[p.document]);

  return (
    <Modal visible transparent animationType="fade" onRequestClose={() => undefined} statusBarTranslucent>
      <View style={[styles.backdrop, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
        <View style={styles.sheet} accessibilityViewIsModal>
          <ScrollView contentContainerStyle={{ padding: 20, gap: 12 }}>
            <Text style={{ fontSize: 36, textAlign: "center" }}>📄</Text>
            <Text style={styles.title} accessibilityRole="header">
              Actualizamos cómo cuidamos tus datos
            </Text>
            <Text style={styles.lead}>
              {`Para seguir usando Sportacus necesitamos que leas y aceptes ${names.length > 1 ? "estos documentos" : "este documento"}. `}
              Tus datos siguen siendo tuyos: puedes exportarlos o borrarlos cuando quieras.
            </Text>
            {pending.map((p) => {
              const doc = docs.data?.find((d) => d.key === p.document);
              const expanded = open === p.document;
              return (
                <View key={p.document} style={styles.docBox}>
                  <PressableScale
                    onPress={() => setOpen(expanded ? null : p.document)}
                    accessibilityRole="button"
                    accessibilityState={{ expanded }}
                    style={styles.docHead}
                  >
                    <Text style={styles.docName}>{NAMES[p.document]}</Text>
                    <Text style={{ color: colors.primary, fontWeight: "700", fontSize: 13 }}>{expanded ? "Ocultar ▴" : "Leer ▾"}</Text>
                  </PressableScale>
                  {expanded ? (
                    doc ? (
                      <LegalDocumentView document={doc} compact />
                    ) : (
                      <Text style={styles.lead}>{docs.error ?? "Cargando el documento…"}</Text>
                    )
                  ) : null}
                </View>
              );
            })}
            <CheckRow checked={checked} onChange={(v) => { setChecked(v); setError(null); }} label={`Acepto ${names.join(" y ")}`} required>
              <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>{`Leí y acepto ${names.join(" y los ")}.`}</Text>
            </CheckRow>
            {error ? <Text style={{ color: colors.danger, fontSize: 12, fontWeight: "600" }}>{error}</Text> : null}
          </ScrollView>
          <View style={styles.footer}>
            <GradientButton title="Cerrar sesión" variant="ghost" onPress={onSignOut} disabled={saving} style={{ flex: 1 }} />
            <GradientButton title="Aceptar y seguir" onPress={accept} loading={saving} style={{ flex: 1.5 }} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const createStyles = ({ colors }: Theme) => ({
  backdrop: { flex: 1, backgroundColor: colors.overlay, paddingHorizontal: 16, justifyContent: "center" as const },
  sheet: { backgroundColor: colors.bg, borderRadius: 24, maxHeight: "100%" as const, width: "100%" as const, maxWidth: 560, alignSelf: "center" as const, overflow: "hidden" as const },
  title: { fontSize: 19, fontWeight: "800" as const, color: colors.text, textAlign: "center" as const },
  lead: { fontSize: 14, color: colors.textMuted, lineHeight: 20, textAlign: "center" as const },
  docBox: { backgroundColor: colors.card, borderRadius: 16, padding: 12, gap: 10 },
  docHead: { flexDirection: "row" as const, alignItems: "center" as const, justifyContent: "space-between" as const, minHeight: 44, gap: 8 },
  docName: { flex: 1, fontSize: 14, fontWeight: "700" as const, color: colors.text },
  footer: { flexDirection: "row" as const, gap: 10, padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
