import React from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { EnrollResponse } from "../../services/types";
import { Theme, deriveAccent, useThemedStyles } from "../../theme";
import { Card, SectionTitle } from "../ui/Card";
import GradientButton from "../ui/GradientButton";
import NoticeBox from "../ui/NoticeBox";
import { FactorList, PlanPreview } from "./PlanPieces";
import WorldHero from "./WorldHero";

interface Props {
  result: EnrollResponse;
  onEnter: () => void;
}

/** Resultado de la inscripción: ruta, nivel, por qué, avisos y vista previa del plan. */
export default function EnrollResult({ result, onEnter }: Props) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const { world, plan } = result;
  const accent = deriveAccent(world.color);

  return (
    <View style={styles.bg}>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        <WorldHero color={world.color} icon={world.icon} title={`¡Te damos la bienvenida a ${world.name}!`} subtitle="Tu plan está listo." back={false}>
          <View style={styles.pills}>
            <View style={styles.pill}>
              <Text style={styles.pillLabel}>Ruta</Text>
              <Text style={styles.pillValue}>{world.trackName}</Text>
            </View>
            <View style={styles.pill}>
              <Text style={styles.pillLabel}>Nivel</Text>
              <Text style={styles.pillValue}>{`${world.level} de 5`}</Text>
            </View>
          </View>
        </WorldHero>

        <View style={{ padding: 16 }}>
          {result.notices.length > 0 ? (
            <View style={{ gap: 8 }}>
              {result.notices.map((n) => (
                <NoticeBox key={n} tone="info" body={n} />
              ))}
            </View>
          ) : null}

          {result.rationale.length > 0 ? (
            <>
              <SectionTitle>Por qué esta ruta</SectionTitle>
              <Card delay={60}>
                <FactorList factors={result.rationale} color={accent.main} />
              </Card>
            </>
          ) : null}

          <SectionTitle>Tu primera semana</SectionTitle>
          <Card delay={120}>
            <PlanPreview plan={plan} color={accent.main} />
            {plan.rationale.length > 0 ? (
              <View style={{ marginTop: 14 }}>
                <Text style={styles.subTitle}>Cómo lo armamos</Text>
                <FactorList factors={plan.rationale} color={accent.main} />
              </View>
            ) : null}
          </Card>
        </View>
      </ScrollView>
      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        <GradientButton title="Entrar a mi mundo" onPress={onEnter} />
      </View>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  pills: { flexDirection: "row" as const, gap: 10, marginTop: 16 },
  pill: { flex: 1, backgroundColor: "rgba(255,255,255,0.16)", borderRadius: 14, padding: 10 },
  pillLabel: { color: "rgba(255,255,255,0.75)", fontSize: 11, fontWeight: "600" as const },
  pillValue: { color: "#fff", fontSize: 15, fontWeight: "800" as const, marginTop: 2 },
  subTitle: { fontSize: 12, fontWeight: "700" as const, color: colors.textMuted, marginBottom: 8 },
  footer: { padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
