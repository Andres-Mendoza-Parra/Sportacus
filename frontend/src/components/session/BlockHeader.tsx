import { Image } from "expo-image";
import { router } from "expo-router";
import React from "react";
import { ActivityIndicator, Text, View } from "react-native";

import { blockTypeLabel, sideLabel } from "../../constants/labels";
import { BlockView } from "../../services/types";
import { useTheme } from "../../theme";
import NoticeBox from "../ui/NoticeBox";
import PressableScale from "../ui/PressableScale";
import { prescriptionSummary } from "./format";

interface Props {
  block: BlockView;
  onSwap?: () => void;
  swapping?: boolean;
}

/** Cabecera de un bloque: tipo, título, imagen, indicaciones (tempo, lado, intensidad), límite de dolor y acciones. */
export default function BlockHeader({ block, onSwap, swapping }: Props) {
  const { colors } = useTheme();
  const p = block.prescription;
  const details = [p.tempo ? `Tempo ${p.tempo}` : null, sideLabel(p.side), p.intensity ? `Intensidad: ${p.intensity}` : null].filter(
    (x): x is string => !!x,
  );

  return (
    <View style={{ gap: 10 }}>
      <Text style={{ fontSize: 11, fontWeight: "700", color: colors.primary, textTransform: "uppercase", letterSpacing: 0.8 }}>
        {blockTypeLabel(block.blockType)}
      </Text>
      <Text style={{ fontSize: 22, fontWeight: "800", color: colors.text }}>{block.title}</Text>
      <Text style={{ fontSize: 14, color: colors.textMuted }}>{prescriptionSummary(p)}</Text>

      {block.imageUrl ? (
        <Image
          source={{ uri: block.imageUrl }}
          contentFit="contain"
          accessibilityLabel={`Imagen de ${block.title}`}
          style={{ width: "100%", height: 180, borderRadius: 16, backgroundColor: colors.cardAlt }}
        />
      ) : null}

      {details.length > 0 ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
          {details.map((d) => (
            <View key={d} style={{ paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, backgroundColor: colors.cardAlt }}>
              <Text style={{ fontSize: 12, color: colors.text, fontWeight: "600" }}>{d}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {p.cue ? <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>🎯 {p.cue}</Text> : null}
      {block.notes ? <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>{block.notes}</Text> : null}
      {p.painMax != null ? <NoticeBox tone="warn" body={`Detente si el dolor supera ${p.painMax}/10.`} /> : null}

      <View style={{ flexDirection: "row", gap: 8 }}>
        {block.movementKey ? (
          <PressableScale
            onPress={() => router.push({ pathname: "/movement/[key]", params: { key: block.movementKey ?? "" } })}
            accessibilityRole="button"
            style={{ flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: "center", backgroundColor: colors.cardAlt }}
          >
            <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>📖 Ver técnica</Text>
          </PressableScale>
        ) : null}
        {onSwap && !block.progressionKey ? (
          <PressableScale
            onPress={onSwap}
            disabled={swapping}
            accessibilityRole="button"
            accessibilityLabel="Cambiar por una alternativa"
            style={{ flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: "center", backgroundColor: colors.cardAlt }}
          >
            {swapping ? (
              <ActivityIndicator color={colors.primary} />
            ) : (
              <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>🔄 Cambiar ejercicio</Text>
            )}
          </PressableScale>
        ) : null}
      </View>
    </View>
  );
}
