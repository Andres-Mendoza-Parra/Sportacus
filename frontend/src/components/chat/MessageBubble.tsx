import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { ChatAction, StoredMessage, describeAction } from "../../services/chatService";
import { Theme, useThemedStyles } from "../../theme";
import Markdown from "../ui/Markdown";
import PressableScale from "../ui/PressableScale";

interface Props {
  message: StoredMessage;
  botIcon: string;
  initials: string;
  onAction: (action: ChatAction) => void;
}

/** Un mensaje del chat: burbuja del bot (markdown + acciones), del usuario o aviso del sistema. */
export default function MessageBubble({ message: m, botIcon, initials, onAction }: Props) {
  const styles = useThemedStyles(createStyles);

  if (m.from === "system") {
    return (
      <Animated.View entering={FadeInDown.duration(300)} style={styles.systemRow}>
        <View style={styles.systemBubble}>
          <Text style={styles.systemText}>✅ {m.text}</Text>
        </View>
      </Animated.View>
    );
  }

  const isBot = m.from === "bot";
  return (
    <Animated.View entering={FadeInDown.duration(300)} style={[styles.msgRow, !isBot && styles.msgRowUser]}>
      <View style={[styles.msgAvatar, isBot ? styles.msgAvatarBot : styles.msgAvatarUser]}>
        <Text style={styles.msgAvatarText}>{isBot ? botIcon : initials}</Text>
      </View>
      <View style={{ maxWidth: "78%" }}>
        <View style={[styles.bubble, isBot ? styles.bubbleBot : styles.bubbleUser, m.personalized && styles.bubblePersonalized]}>
          {isBot ? <Markdown text={m.text} style={styles.bubbleTextBot} /> : <Text style={styles.bubbleTextUser}>{m.text}</Text>}
        </View>
        {m.actions?.length ? (
          <View style={styles.actionsWrap}>
            {m.actions.map((a, idx) => (
              <PressableScale key={idx} style={styles.actionBtn} onPress={() => onAction(a)} accessibilityRole="button">
                <Text>⚡</Text>
                <View style={{ flex: 1 }}>
                  <Text style={styles.actionText}>{describeAction(a)}</Text>
                  <Text style={styles.actionHint}>Toca para confirmar</Text>
                </View>
              </PressableScale>
            ))}
          </View>
        ) : null}
        <Text style={[styles.time, !isBot && { textAlign: "right" }]}>
          {m.time}
          {m.personalized ? " · con tus datos" : ""}
        </Text>
      </View>
    </Animated.View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  msgRow: { flexDirection: "row" as const, alignItems: "flex-end" as const, gap: 8 },
  msgRowUser: { flexDirection: "row-reverse" as const },
  msgAvatar: { width: 30, height: 30, borderRadius: 15, alignItems: "center" as const, justifyContent: "center" as const },
  msgAvatarBot: { backgroundColor: colors.primaryDark },
  msgAvatarUser: { backgroundColor: colors.primary },
  msgAvatarText: { color: "#fff", fontSize: 11, fontWeight: "700" as const },
  bubble: { paddingVertical: 11, paddingHorizontal: 14, borderRadius: 18 },
  bubbleBot: { backgroundColor: colors.card, borderBottomLeftRadius: 4 },
  bubbleUser: { backgroundColor: colors.primary, borderBottomRightRadius: 4 },
  bubblePersonalized: { borderLeftWidth: 3, borderLeftColor: colors.primary },
  bubbleTextBot: { color: colors.text, fontSize: 13, lineHeight: 19 },
  bubbleTextUser: { color: "#fff", fontSize: 13, lineHeight: 19 },
  time: { fontSize: 10, color: colors.textFaint, marginTop: 2, paddingHorizontal: 4 },
  actionsWrap: { gap: 6, marginTop: 6 },
  actionBtn: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 8,
    paddingVertical: 9,
    paddingHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  actionText: { color: colors.primary, fontSize: 12, fontWeight: "600" as const },
  actionHint: { color: colors.textFaint, fontSize: 10, marginTop: 2 },
  systemRow: { alignItems: "center" as const },
  systemBubble: { backgroundColor: colors.primarySoft, borderRadius: 12, paddingVertical: 10, paddingHorizontal: 12, maxWidth: 300 },
  systemText: { color: colors.success, fontSize: 12 },
});
