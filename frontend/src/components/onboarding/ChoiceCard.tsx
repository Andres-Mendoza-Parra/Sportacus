import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  icon: string;
  title: string;
  body: string;
  onPress: () => void;
  delay?: number;
}

/** Tarjeta grande de elección (p. ej. "Ya sé lo que quiero" / "Ayúdame a elegir"). */
export default function ChoiceCard({ icon, title, body, onPress, delay = 0 }: Props) {
  const styles = useThemedStyles(createStyles);
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(400)}>
      <PressableScale style={styles.card} onPress={onPress} accessibilityRole="button" accessibilityLabel={`${title}. ${body}`}>
        <View style={styles.iconWrap}>
          <Text style={{ fontSize: 30 }}>{icon}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.body}>{body}</Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </PressableScale>
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: { flexDirection: "row" as const, alignItems: "center" as const, gap: 14, backgroundColor: colors.card, borderRadius: 20, padding: 18, ...shadow },
  iconWrap: { width: 58, height: 58, borderRadius: 18, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  title: { fontSize: 16, fontWeight: "800" as const, color: colors.text },
  body: { fontSize: 13, color: colors.textMuted, marginTop: 3, lineHeight: 18 },
  chevron: { fontSize: 28, color: colors.textFaint },
});
