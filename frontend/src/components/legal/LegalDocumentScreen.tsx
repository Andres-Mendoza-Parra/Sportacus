import React from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useLoader } from "../../hooks/useLoader";
import { fetchLegalDocuments } from "../../services/legalService";
import { LegalDocumentKey } from "../../services/types";
import { useTheme } from "../../theme";
import ScreenHeader from "../ui/ScreenHeader";
import { SkeletonBlock } from "../ui/Skeleton";
import { EmptyState, ErrorState } from "../ui/States";
import LegalDocumentView from "./LegalDocumentView";

const FALLBACK_TITLES: Record<LegalDocumentKey, string> = {
  politica_datos: "Política de tratamiento de datos",
  terminos: "Términos y condiciones",
};

/** Pantalla pública (sin sesión) con un documento legal vigente. */
export default function LegalDocumentScreen({ docKey }: { docKey: LegalDocumentKey }) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const docs = useLoader(fetchLegalDocuments);
  const document = docs.data?.find((d) => d.key === docKey) ?? null;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title={document?.title ?? FALLBACK_TITLES[docKey]} subtitle="Ley 1581 de 2012 · Decreto 1377 de 2013" fallback="/" />
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: insets.bottom + 32, maxWidth: 720, width: "100%", alignSelf: "center" }}
        refreshControl={<RefreshControl refreshing={docs.refreshing} onRefresh={docs.reload} tintColor={colors.primary} />}
      >
        {docs.loading && !docs.data ? (
          <View style={{ gap: 12 }}>
            <SkeletonBlock height={140} radius={18} />
            <SkeletonBlock height={140} radius={18} />
          </View>
        ) : document ? (
          <LegalDocumentView document={document} />
        ) : docs.error && !docs.data ? (
          <ErrorState message={docs.error} onRetry={docs.reload} />
        ) : (
          <EmptyState icon="📄" title="Documento no disponible" body="Intenta de nuevo en unos minutos." actionLabel="Reintentar" onAction={docs.reload} />
        )}
        {document ? (
          <Text style={{ fontSize: 12, color: colors.textMuted, textAlign: "center", marginTop: 18, lineHeight: 17 }}>
            Puedes exportar o borrar tus datos cuando quieras desde Cuenta › Privacidad y datos.
          </Text>
        ) : null}
      </ScrollView>
    </View>
  );
}
