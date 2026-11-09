import React from "react";
import { ScrollView, Text, View } from "react-native";

import { StepStatus, TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import WidgetFrame from "./WidgetFrame";

const STATUS_LABEL: Record<StepStatus, string> = { done: "hecho", current: "actual", locked: "bloqueado" };

/** Árboles de habilidades: escalones hechos / actual / bloqueados y avance de cada árbol. */
export default function SkillTreeWidget({ widget, delay }: { widget: TypedWidget<"skill_tree">; delay?: number }) {
  const { colors } = useTheme();
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ gap: 16 }}>
        {widget.data.trees.map((tree) => {
          const pct = tree.total > 0 ? (tree.current / tree.total) * 100 : 0;
          return (
            <View key={tree.key}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                {tree.icon ? <Text style={{ fontSize: 18 }}>{tree.icon}</Text> : null}
                <Text style={{ flex: 1, fontSize: 14, fontWeight: "700", color: colors.text }}>{tree.name}</Text>
                <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary }}>
                  {tree.current}/{tree.total}
                </Text>
              </View>
              <AnimatedBar pct={pct} height={6} style={{ marginTop: 6 }} />
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6, paddingTop: 8 }}>
                {tree.steps.map((step) => {
                  const done = step.status === "done";
                  const current = step.status === "current";
                  return (
                    <View
                      key={step.index}
                      accessible
                      accessibilityLabel={`${step.name}: ${STATUS_LABEL[step.status] ?? step.status}`}
                      style={{
                        paddingHorizontal: 10,
                        paddingVertical: 6,
                        borderRadius: 999,
                        borderWidth: 1.5,
                        borderColor: current ? colors.primary : done ? colors.primarySoft : colors.border,
                        backgroundColor: done ? colors.primarySoft : current ? colors.card : colors.cardAlt,
                        opacity: step.status === "locked" ? 0.6 : 1,
                      }}
                    >
                      <Text style={{ fontSize: 11, fontWeight: current ? "800" : "600", color: done || current ? colors.primary : colors.textFaint }}>
                        {done ? "✓ " : current ? "▶ " : "🔒 "}
                        {step.name}
                      </Text>
                    </View>
                  );
                })}
              </ScrollView>
            </View>
          );
        })}
      </View>
    </WidgetFrame>
  );
}
