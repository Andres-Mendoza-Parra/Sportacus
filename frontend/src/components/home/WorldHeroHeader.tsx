import React from "react";
import { Text, View } from "react-native";

import { Streak, WorldHeader } from "../../services/types";
import GradientHeader from "../ui/GradientHeader";

interface Props {
  greeting?: string | null;
  world: WorldHeader;
  streak?: Streak | null;
  /** Texto bajo el saludo; por defecto la ruta y el nivel. */
  caption?: string;
  children?: React.ReactNode;
}

/** Cabecera de las pestañas del mundo: saludo, mundo, ruta, nivel y racha. */
export default function WorldHeroHeader({ greeting, world, streak, caption, children }: Props) {
  const days = streak?.currentDays ?? 0;
  return (
    <GradientHeader rounded style={{ paddingBottom: 22 }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
        <View style={{ width: 52, height: 52, borderRadius: 18, backgroundColor: "rgba(255,255,255,0.18)", alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: 28 }}>{world.icon}</Text>
        </View>
        <View style={{ flex: 1 }}>
          {greeting ? (
            <Text style={{ color: "#fff", fontSize: 20, fontWeight: "800" }} numberOfLines={1}>
              {greeting}
            </Text>
          ) : null}
          <Text style={{ color: "rgba(255,255,255,0.85)", fontSize: 13, marginTop: 2 }} numberOfLines={1}>
            {caption ?? [world.name, world.trackName].filter(Boolean).join(" · ")}
          </Text>
        </View>
      </View>
      <View style={{ flexDirection: "row", gap: 8, marginTop: 16, flexWrap: "wrap" }}>
        {world.key !== "base" ? <Pill text={`⭐ Nivel ${world.level}`} /> : null}
        <Pill text={`🔥 ${days} ${days === 1 ? "día" : "días"} de racha`} />
        {streak && streak.longestDays > 0 ? <Pill text={`🏆 Récord ${streak.longestDays}`} /> : null}
      </View>
      {children}
    </GradientHeader>
  );
}

function Pill({ text }: { text: string }) {
  return (
    <View style={{ backgroundColor: "rgba(255,255,255,0.18)", borderRadius: 999, paddingVertical: 6, paddingHorizontal: 12 }}>
      <Text style={{ color: "#fff", fontSize: 12, fontWeight: "700" }}>{text}</Text>
    </View>
  );
}
