import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown, ZoomIn } from "react-native-reanimated";

import { SessionResult } from "../../services/types";
import { useTheme } from "../../theme";
import { Card, SectionTitle } from "../ui/Card";
import NoticeBox from "../ui/NoticeBox";

/** Resultado de la sesión: racha, desbloqueos, logros nuevos y lo más destacado. */
export default function SessionResultView({ result }: { result: SessionResult }) {
  const { colors } = useTheme();
  const streak = result.streak.currentDays;

  return (
    <View>
      <Animated.View entering={ZoomIn.springify().damping(14)} style={{ alignItems: "center", paddingVertical: 16 }}>
        <Text style={{ fontSize: 64 }}>🏅</Text>
        <Text style={{ fontSize: 24, fontWeight: "800", color: colors.text, marginTop: 6 }}>¡Sesión registrada!</Text>
        <Text style={{ fontSize: 15, color: colors.textMuted, marginTop: 4 }}>
          🔥 Racha de {streak} {streak === 1 ? "día" : "días"}
          {result.streak.longestDays > streak ? ` · mejor: ${result.streak.longestDays}` : ""}
        </Text>
      </Animated.View>

      {result.unlocks.length > 0 ? (
        <View style={{ gap: 10 }}>
          {result.unlocks.map((u, i) => (
            <Animated.View key={`${u.progressionKey}-${u.toStep}`} entering={FadeInDown.delay(150 + i * 80).duration(350)}>
              <NoticeBox tone="success" icon="🔓" title="¡Desbloqueaste un escalón!" body={`¡Desbloqueaste ${u.stepName} en ${u.progressionName}!`} />
            </Animated.View>
          ))}
        </View>
      ) : null}

      {result.achievements.length > 0 ? (
        <>
          <SectionTitle>Logros nuevos</SectionTitle>
          <View style={{ gap: 8 }}>
            {result.achievements.map((a, i) => (
              <Card key={a.key} delay={200 + i * 80} style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
                <Text style={{ fontSize: 30 }}>{a.icon ?? "🏆"}</Text>
                <Text style={{ flex: 1, fontSize: 15, fontWeight: "700", color: colors.text }}>{a.title}</Text>
              </Card>
            ))}
          </View>
        </>
      ) : null}

      {result.highlights.length > 0 ? (
        <>
          <SectionTitle>Para la próxima</SectionTitle>
          <Card delay={260} style={{ gap: 8 }}>
            {result.highlights.map((h) => (
              <Text key={h} style={{ fontSize: 14, color: colors.text, lineHeight: 20 }}>
                ✨ {h}
              </Text>
            ))}
          </Card>
        </>
      ) : null}

      {result.planRegenerated ? (
        <NoticeBox tone="info" style={{ marginTop: 16 }} body="Tu plan se actualizó con base en esta sesión. Revísalo en la pestaña Plan." />
      ) : null}
    </View>
  );
}
