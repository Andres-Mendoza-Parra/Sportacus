import React from "react";
import { Text, View } from "react-native";

import { formatDate } from "../../constants/labels";
import { LegalDocument } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";

/** Texto de un documento legal: versión, fecha y secciones (título + cuerpo con párrafos). */
export default function LegalDocumentView({ document, compact }: { document: LegalDocument; compact?: boolean }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: compact ? 10 : 12 }}>
      <Text style={styles.meta}>
        {`Versión ${document.version}`}
        {document.updatedAt ? ` · actualizada el ${formatDate(document.updatedAt, true)}` : ""}
      </Text>
      {document.summary ? <Text style={styles.summary}>{document.summary}</Text> : null}
      {document.sections.map((section, i) => (
        <Card key={`${i}-${section.title}`} style={compact ? styles.compactCard : undefined}>
          <Text style={styles.title} accessibilityRole="header">
            {section.title}
          </Text>
          {section.body
            .split(/\n{2,}/)
            .map((p) => p.trim())
            .filter(Boolean)
            .map((p, j) => (
              <Text key={j} style={styles.body}>
                {p}
              </Text>
            ))}
        </Card>
      ))}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  meta: { fontSize: 12, color: colors.textMuted },
  summary: { fontSize: 14, color: colors.text, lineHeight: 21, fontWeight: "600" as const },
  title: { fontSize: 15, fontWeight: "700" as const, color: colors.text, marginBottom: 6 },
  body: { fontSize: 14, color: colors.text, lineHeight: 21, marginTop: 4 },
  compactCard: { padding: 14, borderWidth: 1, borderColor: colors.border, boxShadow: "none" },
});
