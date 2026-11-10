import { Image } from "expo-image";
import React from "react";
import { Text, View } from "react-native";

import { blockTypeIcon } from "../../constants/labels";
import { BlockView } from "../../services/types";
import { useTheme } from "../../theme";

interface Props {
  block: Pick<BlockView, "imageUrl" | "blockType" | "title">;
  size?: number;
  /** Número del bloque en la sesión (insignia en la esquina). */
  index?: number;
}

/** Miniatura del bloque: imagen del ejercicio o, si no hay, el ícono de su tipo. */
export default function BlockThumb({ block, size = 56, index }: Props) {
  const { colors } = useTheme();
  const radius = Math.round(size * 0.26);
  return (
    <View style={{ width: size, height: size }}>
      {block.imageUrl ? (
        <Image
          source={{ uri: block.imageUrl }}
          contentFit="cover"
          transition={150}
          accessibilityLabel={`Imagen de ${block.title}`}
          style={{ width: size, height: size, borderRadius: radius, backgroundColor: "#fff", borderWidth: 1, borderColor: colors.border }}
        />
      ) : (
        <View style={{ width: size, height: size, borderRadius: radius, backgroundColor: colors.primarySoft, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: Math.round(size * 0.42) }}>{blockTypeIcon(block.blockType)}</Text>
        </View>
      )}
      {index != null ? (
        <View
          style={{
            position: "absolute",
            top: -5,
            left: -5,
            minWidth: 20,
            height: 20,
            paddingHorizontal: 4,
            borderRadius: 10,
            backgroundColor: colors.primary,
            alignItems: "center",
            justifyContent: "center",
            borderWidth: 2,
            borderColor: colors.card,
          }}
        >
          <Text style={{ fontSize: 10, fontWeight: "800", color: colors.onPrimary }}>{index}</Text>
        </View>
      ) : null}
    </View>
  );
}
