import React from "react";
import { Text, View } from "react-native";

import { useActionRoute } from "../../hooks/useActionRoute";
import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import { noticeColors, toNoticeKind } from "../ui/NoticeBox";
import PressableScale from "../ui/PressableScale";
import WidgetFrame from "./WidgetFrame";

/** Consejos personalizados; los que traen `actionRoute` llevan a la pantalla indicada. */
export default function InsightsWidget({ widget, delay }: { widget: TypedWidget<"insights">; delay?: number }) {
  const { colors } = useTheme();
  const open = useActionRoute();
  if (widget.data.items.length === 0) return null;

  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ gap: 10 }}>
        {widget.data.items.map((item, i) => {
          const tone = noticeColors(toNoticeKind(item.tone), colors);
          return (
            <View key={`${item.title}-${i}`} style={{ flexDirection: "row", gap: 12, padding: 12, borderRadius: 14, backgroundColor: colors.cardAlt }}>
              <View style={{ width: 38, height: 38, borderRadius: 12, backgroundColor: tone.bg, alignItems: "center", justifyContent: "center" }}>
                <Text style={{ fontSize: 18 }}>{item.icon ?? "💡"}</Text>
              </View>
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={{ fontSize: 14, fontWeight: "700", color: colors.text }}>{item.title}</Text>
                <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 18 }}>{item.body}</Text>
                {item.actionRoute ? (
                  <PressableScale
                    onPress={() => open(item.actionRoute ?? "")}
                    accessibilityRole="button"
                    style={{ alignSelf: "flex-start", marginTop: 8, paddingVertical: 7, paddingHorizontal: 12, borderRadius: 10, backgroundColor: colors.primary }}
                  >
                    <Text style={{ color: colors.onPrimary, fontSize: 12, fontWeight: "700" }}>{item.actionLabel ?? "Ver"} →</Text>
                  </PressableScale>
                ) : null}
              </View>
            </View>
          );
        })}
      </View>
    </WidgetFrame>
  );
}
