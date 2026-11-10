import { router } from "expo-router";
import React from "react";
import { ActivityIndicator, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { blockTypeLabel, weekdayName } from "../../constants/labels";
import { BlockView, SessionView, SessionUi } from "../../services/types";
import { useTheme } from "../../theme";
import { Card, SectionTitle } from "../ui/Card";
import PressableScale from "../ui/PressableScale";
import BlockThumb from "./BlockThumb";
import { prescriptionSummary } from "./format";

interface Props {
  session: SessionView;
  blocks: BlockView[];
  ui: SessionUi;
  onSwap: (block: BlockView) => void;
  swappingId: string | null;
}

/** Vista previa de la sesión: datos generales, consejos y bloques (miniatura, dosis y por qué); cada uno abre su detalle. */
export default function SessionPreview({ session, blocks, ui, onSwap, swappingId }: Props) {
  const { colors } = useTheme();
  const meta = [
    session.weekday ? weekdayName(session.weekday) : null,
    session.estMinutes ? `~${session.estMinutes} min` : null,
    `${blocks.length} ${blocks.length === 1 ? "bloque" : "bloques"}`,
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

      <SectionTitle>Bloques · toca uno para ver por qué y cómo</SectionTitle>
      <View style={{ gap: 10 }}>
        {blocks.map((block, i) => (
          <Animated.View key={block.id} entering={FadeInDown.delay(i * 50).duration(300)}>
            <Card style={{ padding: 12, flexDirection: "row", alignItems: "center", gap: 12 }}>
              <PressableScale
                style={{ flex: 1, flexDirection: "row", alignItems: "center", gap: 12 }}
                accessibilityRole="button"
                accessibilityLabel={`Bloque ${i + 1}: ${block.title}. ${prescriptionSummary(block.prescription)}`}
                accessibilityHint="Abre por qué y cómo hacer este ejercicio"
                onPress={() => router.push({ pathname: "/block/[id]", params: { id: block.id } })}
              >
                <BlockThumb block={block} index={i + 1} />
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: 10, fontWeight: "700", color: colors.textFaint, textTransform: "uppercase" }}>
                    {blockTypeLabel(block.blockType)}
                  </Text>
                  <Text style={{ fontSize: 15, fontWeight: "700", color: colors.text }} numberOfLines={2}>
                    {block.title}
                  </Text>
                  <Text style={{ fontSize: 12, color: colors.primary, fontWeight: "600", marginTop: 2 }}>{prescriptionSummary(block.prescription)}</Text>
                  {block.summary ? (
                    <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 3, lineHeight: 17 }} numberOfLines={2}>
                      💡 {block.summary}
                    </Text>
                  ) : null}
                </View>
                <Text style={{ fontSize: 22, color: colors.textFaint }}>›</Text>
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
