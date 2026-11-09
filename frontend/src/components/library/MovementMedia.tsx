import { Image } from "expo-image";
import React, { useState } from "react";
import { LayoutChangeEvent, ScrollView, Text, View } from "react-native";

import { MuscleView } from "../../services/types";
import { useTheme } from "../../theme";

interface Props {
  imageUrls: string[];
  muscleViews: MuscleView[];
  name: string;
}

/** Carrusel horizontal de imágenes del movimiento + vistas de músculos (base con capas superpuestas). */
export default function MovementMedia({ imageUrls, muscleViews, name }: Props) {
  const { colors } = useTheme();
  const [width, setWidth] = useState(0);
  const [page, setPage] = useState(0);
  const slides = imageUrls.length + muscleViews.length;

  if (slides === 0) return null;

  function onLayout(e: LayoutChangeEvent) {
    setWidth(e.nativeEvent.layout.width);
  }

  const height = Math.min(260, width * 0.75 || 220);

  return (
    <View onLayout={onLayout} style={{ borderRadius: 18, overflow: "hidden", backgroundColor: colors.card }}>
      {width > 0 ? (
        <ScrollView
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          onMomentumScrollEnd={(e) => setPage(Math.round(e.nativeEvent.contentOffset.x / width))}
          scrollEventThrottle={16}
        >
          {imageUrls.map((url, i) => (
            <Image
              key={url}
              source={{ uri: url }}
              style={{ width, height, backgroundColor: "#fff" }}
              contentFit="contain"
              transition={200}
              accessibilityLabel={`${name}, imagen ${i + 1}`}
            />
          ))}
          {muscleViews.map((view, i) => (
            <View key={`${view.baseUrl}-${i}`} style={{ width, height, backgroundColor: "#fff" }} accessibilityLabel={`Músculos que trabaja ${name}`}>
              <Image source={{ uri: view.baseUrl }} style={{ position: "absolute", width, height }} contentFit="contain" />
              {view.overlayUrls.map((overlay) => (
                <Image key={overlay} source={{ uri: overlay }} style={{ position: "absolute", width, height }} contentFit="contain" />
              ))}
            </View>
          ))}
        </ScrollView>
      ) : (
        <View style={{ height: 220 }} />
      )}
      {slides > 1 ? (
        <View style={{ position: "absolute", bottom: 8, left: 0, right: 0, flexDirection: "row", justifyContent: "center", gap: 5 }}>
          {Array.from({ length: slides }).map((_, i) => (
            <View key={i} style={{ width: i === page ? 16 : 6, height: 6, borderRadius: 3, backgroundColor: i === page ? colors.primary : "rgba(0,0,0,0.25)" }} />
          ))}
        </View>
      ) : null}
      {slides > 1 ? (
        <Text style={{ position: "absolute", top: 8, right: 10, fontSize: 11, color: "#555" }}>
          {page + 1}/{slides}
        </Text>
      ) : null}
    </View>
  );
}
