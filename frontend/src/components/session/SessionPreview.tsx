import { router } from "expo-router";
import React from "react";
import { ActivityIndicator, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { blockTypeLabel, weekdayName } from "../../constants/labels";
import { BlockView, SessionView, SessionUi } from "../../services/types";
import { useTheme } from "../../theme";
import { Card, SectionTitle } from "../ui/Card";
import PressableScale from "../ui/PressableScale";
import { prescriptionSummary } from "./format";

interface Props {
  session: SessionView;
  blocks: BlockView[];
  ui: SessionUi;
  onSwap: (block: BlockView) => void;
  swappingId: string | null;
}

/** Vista previa de la sesión: datos generales, consejos y lista de bloques con su prescripción. */
export default function SessionPreview({ session, blocks, ui, onSwap, swappingId }: Props) {
  const { colors } = useTheme();
  const meta = [
    session.weekday ? weekdayName(session.weekday) : null,
    session.estMinutes ? `~${session.estMinutes} min` : null,
    `${blocks.length} bloques`,
    session.done ? "✓ Hecha esta semana" : null,
  ].filter(Boolean);

  return (
    <View>
      <Card>
        <Text style={{ fontSize: 13, color: colors.textMuted }}>{meta.join(" · ")}</Text>
        {session.focus ? <Text style={{ fontSize: 14, color: colors.text, marginTop: 6, lineHeight: 20 }}>{session.focus}</Text> : null}
        {session.notes ? <Text style={{ fontSize: 13, color: colors.textMuted, marginTop: 6, lineHeight: 19 }}>{session.notes}</Text> : null}
      </Card>

      {ui.tips.length > 0 ? (
        <>
          <SectionTitle>Consejos</SectionTitle>
          <Card style={{ gap: 6 }}>
            {ui.tips.map((tip) => (
              <Text key={tip} style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>
                💡 {tip}
              </Text>
            ))}
          </Card>
        </>
      ) : null}

      <SectionTitle>Bloques</SectionTitle>
      <View style={{ gap: 10 }}>
        {blocks.map((block, i) => (
          <Animated.View key={block.id} entering={FadeInDown.delay(i * 50).duration(300)}>
            <Card style={{ padding: 14, flexDirection: "row", alignItems: "center", gap: 12 }}>
              <View style={{ width: 30, height: 30, borderRadius: 10, backgroundColor: colors.primarySoft, alignItems: "center", justifyContent: "center" }}>
                <Text style={{ fontSize: 13, fontWeight: "800", color: colors.primary }}>{i + 1}</Text>
              </View>
              <PressableScale
                style={{ flex: 1 }}
                disabled={!block.movementKey}
                accessibilityRole="button"
                accessibilityLabel={`Ver técnica de ${block.title}`}
                onPress={() => block.movementKey && router.push({ pathname: "/movement/[key]", params: { key: block.movementKey } })}
              >
                <Text style={{ fontSize: 10, fontWeight: "700", color: colors.textFaint, textTransform: "uppercase" }}>
                  {blockTypeLabel(block.blockType)}
                </Text>
                <Text style={{ fontSize: 15, fontWeight: "700", color: colors.text }}>{block.title}</Text>
                <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2 }}>{prescriptionSummary(block.prescription)}</Text>
              </PressableScale>
              {!block.progressionKey ? (
                <PressableScale
                  onPress={() => onSwap(block)}
                  disabled={swappingId !== null}
                  accessibilityRole="button"
                  accessibilityLabel={`Cambiar ${block.title} por una alternativa`}
                  style={{ width: 40, height: 40, borderRadius: 12, backgroundColor: colors.cardAlt, alignItems: "center", justifyContent: "center" }}
                >
                  {swappingId === block.id ? <ActivityIndicator color={colors.primary} /> : <Text style={{ fontSize: 16 }}>🔄</Text>}
                </PressableScale>
              ) : null}
            </Card>
          </Animated.View>
        ))}
      </View>
    </View>
  );
}
