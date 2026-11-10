import { useLocalSearchParams } from "expo-router";
import { useEffect, useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Switch, Text, TextInput, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import MessageBubble from "../../components/chat/MessageBubble";
import { TourTarget } from "../../components/tour/TourContext";
import TypingDots from "../../components/chat/TypingDots";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import { useAuth } from "../../context/AuthContext";
import { useWorld } from "../../context/WorldContext";
import { track } from "../../services/analytics";
import { extractErrorMessage } from "../../services/api";
import {
  ChatAction,
  StoredMessage,
  askAssistant,
  loadChatHistory,
  parseReply,
  runAction,
  saveChatHistory,
  startNewConversation,
  suggestionsFor,
} from "../../services/chatService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const WELCOME =
  "¡Hola! Soy tu asistente de Sportacus. Puedo responderte dudas sobre ejercicio, técnica, nutrición y hábitos saludables.\n\n" +
  "Si activas «Usar mis datos» conoceré tu mundo activo, tu ruta, tu nivel, tu salud y tu próxima sesión, y podré regenerar tu plan. 💪";

function nowLabel() {
  return new Date().toLocaleTimeString("es", { hour: "2-digit", minute: "2-digit" });
}

function welcomeMessage(): StoredMessage {
  return { id: 0, from: "bot", text: WELCOME, time: nowLabel() };
}

/** Pestaña Asistente: chat con el agente local; el backend arma el contexto del mundo activo. */
export default function ChatScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();
  const { user } = useAuth();
  const { active, invalidate } = useWorld();
  const params = useLocalSearchParams<{ prompt?: string; t?: string }>();

  const [messages, setMessages] = useState<StoredMessage[]>([welcomeMessage()]);
  const [input, setInput] = useState("");
  const [useMyData, setUseMyData] = useState(true);
  const [isSending, setIsSending] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const scrollRef = useRef<React.ComponentRef<typeof ScrollView>>(null);
  const nextId = useRef(1);
  const handledPrompt = useRef<string | null>(null);

  const initials = (user?.name || user?.email || "?").trim().slice(0, 2).toUpperCase();
  const suggestions = suggestionsFor(active?.name ?? null);

  function push(message: Omit<StoredMessage, "id" | "time">) {
    setMessages((prev) => [...prev, { ...message, id: nextId.current++, time: nowLabel() }]);
  }

  // Recupera la conversación guardada en el dispositivo.
  useEffect(() => {
    (async () => {
      const stored = await loadChatHistory();
      if (stored && stored.messages.length > 0) {
        setMessages(stored.messages);
        setUseMyData(stored.profileLoaded);
        nextId.current = Math.max(...stored.messages.map((m) => m.id)) + 1;
      }
      setHydrated(true);
    })();
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    saveChatHistory({ profileLoaded: useMyData, messages }).catch(() => {});
  }, [hydrated, useMyData, messages]);

  async function send(text: string) {
    const question = text.trim();
    if (!question || isSending) return;
    setInput("");
    push({ from: "user", text: question });
    setIsSending(true);
    track("assistant_message", { personalized: useMyData });
    try {
      const response = await askAssistant(question, useMyData);
      const { text: reply, actions } = parseReply(response.reply);
      push({ from: "bot", text: reply, personalized: response.personalized, actions: actions.length ? actions : undefined });
    } catch (error) {
      push({ from: "bot", text: `No pude responder: ${error instanceof Error ? error.message : extractErrorMessage(error)}` });
    } finally {
      setIsSending(false);
    }
  }

  // Preguntas que llegan desde otras pantallas (p. ej. «Preguntar al asistente» en un movimiento).
  useEffect(() => {
    if (!hydrated || !params.prompt) return;
    const key = `${params.prompt}|${params.t ?? ""}`;
    if (handledPrompt.current === key) return;
    handledPrompt.current = key;
    send(String(params.prompt));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, params.prompt, params.t]);

  async function applyAction(messageId: number, action: ChatAction) {
    const ok = await confirm({
      title: "¿Regenerar tu plan?",
      message: "El asistente propone armar un plan nuevo con tus datos actuales. Tu historial se conserva.",
      confirmText: "Regenerar",
    });
    if (!ok) return;
    try {
      const result = await runAction(action);
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, actions: undefined } : m)));
      invalidate();
      push({ from: "system", text: result });
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }

  async function newConversation() {
    const ok = await confirm({
      title: "¿Empezar una conversación nueva?",
      message: "Se borrará el historial guardado en este dispositivo.",
      confirmText: "Empezar de nuevo",
    });
    if (!ok) return;
    await startNewConversation();
    nextId.current = 1;
    setMessages([welcomeMessage()]);
  }

  return (
    <KeyboardAvoidingView style={styles.bg} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <GradientHeader style={{ paddingBottom: 14 }}>
        <View style={styles.headRow}>
          <View style={styles.avatarBot}>
            <Text style={{ fontSize: 22 }}>🤖</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.headName}>Asistente</Text>
            <Text style={styles.headStatus}>{active ? `Especialista en ${active.name} ${active.icon}` : "Asistente de actividad física"}</Text>
          </View>
          <PressableScale style={styles.newBtn} onPress={newConversation} accessibilityRole="button" accessibilityLabel="Nueva conversación">
            <Text style={styles.newBtnText}>✨ Nueva</Text>
          </PressableScale>
        </View>
        <View style={styles.dataRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.dataTitle}>Usar mis datos</Text>
            <Text style={styles.dataSub}>{useMyData ? "Respuestas según tu mundo, ruta, nivel y salud" : "Respuestas generales"}</Text>
          </View>
          <Switch
            value={useMyData}
            onValueChange={setUseMyData}
            trackColor={{ false: "rgba(255,255,255,0.3)", true: "rgba(255,255,255,0.6)" }}
            thumbColor="#fff"
            accessibilityLabel="Usar mis datos en las respuestas"
          />
        </View>
      </GradientHeader>

      <ScrollView
        ref={scrollRef}
        style={{ flex: 1 }}
        contentContainerStyle={styles.messages}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
        keyboardShouldPersistTaps="handled"
      >
        {messages.map((m) => (
          <MessageBubble key={m.id} message={m} botIcon="🤖" initials={initials} onAction={(a) => applyAction(m.id, a)} />
        ))}
        {isSending ? (
          <Animated.View entering={FadeInDown.duration(200)} style={styles.typingRow}>
            <View style={styles.typingAvatar}>
              <Text style={{ color: "#fff", fontSize: 11 }}>🤖</Text>
            </View>
            <View style={styles.typingBubble}>
              <TypingDots />
            </View>
          </Animated.View>
        ) : null}
      </ScrollView>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.suggestions} contentContainerStyle={{ paddingHorizontal: 12, gap: 7 }} keyboardShouldPersistTaps="handled">
        {suggestions.map((s) => (
          <PressableScale key={s} style={styles.sug} onPress={() => send(s)} accessibilityRole="button">
            <Text style={styles.sugText}>{s}</Text>
          </PressableScale>
        ))}
      </ScrollView>

      <TourTarget id="chat-input" style={styles.inputWrap}>
        <TextInput
          style={styles.input}
          placeholder="Escribe tu pregunta..."
          placeholderTextColor={colors.textFaint}
          value={input}
          onChangeText={setInput}
          onSubmitEditing={() => send(input)}
          returnKeyType="send"
          maxLength={500}
        />
        <Pressable
          style={[styles.sendBtn, (!input.trim() || isSending) && styles.sendBtnDisabled]}
          onPress={() => send(input)}
          disabled={!input.trim() || isSending}
          accessibilityRole="button"
          accessibilityLabel="Enviar mensaje"
        >
          <Text style={styles.sendIcon}>➤</Text>
        </Pressable>
      </TourTarget>
    </KeyboardAvoidingView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  avatarBot: { width: 46, height: 46, borderRadius: 23, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  headName: { color: "#fff", fontSize: 17, fontWeight: "700" as const },
  headStatus: { color: "rgba(255,255,255,0.8)", fontSize: 12, marginTop: 1 },
  newBtn: { paddingVertical: 7, paddingHorizontal: 12, borderRadius: 16, backgroundColor: "rgba(255,255,255,0.18)" },
  newBtnText: { color: "#fff", fontSize: 12, fontWeight: "600" as const },
  dataRow: {
    marginTop: 12,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 10,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 14,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  dataTitle: { color: "#fff", fontSize: 13, fontWeight: "600" as const },
  dataSub: { color: "rgba(255,255,255,0.75)", fontSize: 11 },
  messages: { padding: 12, gap: 12 },
  typingRow: { flexDirection: "row" as const, alignItems: "flex-end" as const, gap: 8 },
  typingAvatar: { width: 30, height: 30, borderRadius: 15, backgroundColor: colors.primaryDark, alignItems: "center" as const, justifyContent: "center" as const },
  typingBubble: { paddingVertical: 11, paddingHorizontal: 14, borderRadius: 18, backgroundColor: colors.card, borderBottomLeftRadius: 4 },
  suggestions: { flexGrow: 0, paddingVertical: 8 },
  sug: { paddingVertical: 8, paddingHorizontal: 14, borderRadius: 20, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
  sugText: { color: colors.primary, fontSize: 12, fontWeight: "600" as const },
  inputWrap: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 8,
    backgroundColor: colors.card,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  input: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 22,
    fontSize: 13,
    backgroundColor: colors.inputBg,
    color: colors.text,
  },
  sendBtn: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.primary, alignItems: "center" as const, justifyContent: "center" as const },
  sendBtnDisabled: { opacity: 0.45 },
  sendIcon: { color: "#fff", fontSize: 16 },
});
