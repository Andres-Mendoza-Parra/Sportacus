import { useFocusEffect, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import Animated, {
  FadeInDown,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from "react-native-reanimated";

import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientHeader from "../../components/ui/GradientHeader";
import Markdown from "../../components/ui/Markdown";
import PressableScale from "../../components/ui/PressableScale";
import { useAuth } from "../../context/AuthContext";
import { extractErrorMessage } from "../../services/api";
import {
  ChatAction,
  StoredMessage,
  askAssistant,
  describeAction,
  getSuggestions,
  isProfileContextStale,
  loadChatHistory,
  loadProfileContext,
  parseReply,
  runAction,
  saveChatHistory,
  startNewConversation,
} from "../../services/chatService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const WELCOME =
  "¡Hola! Soy tu asistente de fitness. Puedo responderte dudas sobre ejercicio, nutrición y hábitos saludables.\n\n" +
  "Si cargas tu perfil, mis respuestas serán personalizadas para ti y podré ayudarte a cambiar ejercicios o regenerar tu rutina. 💪";

const BASE_SUGGESTIONS = [
  "¿Cuánto debo descansar?",
  "Rutina de calentamiento",
  "¿Qué comer antes de entrenar?",
  "Progresión de carga",
];

function nowLabel() {
  return new Date().toLocaleTimeString("es", { hour: "2-digit", minute: "2-digit" });
}

function welcomeMessage(): StoredMessage {
  return { id: 0, from: "bot", text: WELCOME, time: nowLabel() };
}

/** Tres puntos que laten mientras el asistente "escribe". */
function TypingDots() {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: 4, paddingVertical: 4 }}>
      {[0, 1, 2].map((i) => (
        <Dot key={i} delay={i * 150} color={colors.primaryDark} />
      ))}
    </View>
  );
}

function Dot({ delay, color }: { delay: number; color: string }) {
  const opacity = useSharedValue(0.3);
  useEffect(() => {
    opacity.value = withDelay(delay, withRepeat(withSequence(withTiming(1, { duration: 400 }), withTiming(0.3, { duration: 400 })), -1));
  }, [delay, opacity]);
  const style = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return <Animated.View style={[{ width: 7, height: 7, borderRadius: 4, backgroundColor: color }, style]} />;
}

export default function ChatScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();
  const { user } = useAuth();
  const params = useLocalSearchParams<{ prompt?: string; t?: string }>();

  const [messages, setMessages] = useState<StoredMessage[]>([welcomeMessage()]);
  const [input, setInput] = useState("");
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isLoadingProfile, setIsLoadingProfile] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>(BASE_SUGGESTIONS);
  const scrollRef = useRef<React.ComponentRef<typeof ScrollView>>(null);
  const nextId = useRef(1);
  const handledPrompt = useRef<string | null>(null);

  const initials = (user?.name || user?.email || "?").trim().slice(0, 2).toUpperCase();

  function push(message: Omit<StoredMessage, "id" | "time">) {
    setMessages((prev) => [...prev, { ...message, id: nextId.current++, time: nowLabel() }]);
  }

  // Recupera la conversación guardada (y reconstruye el contexto si ya estaba cargado).
  useEffect(() => {
    (async () => {
      const stored = await loadChatHistory();
      if (stored && stored.messages.length > 0) {
        setMessages(stored.messages);
        nextId.current = Math.max(...stored.messages.map((m) => m.id)) + 1;
        if (stored.profileLoaded) {
          try {
            await loadProfileContext();
            setProfileLoaded(true);
            setSuggestions(getSuggestions());
          } catch {
            // sin conexión: el usuario puede volver a pulsar «Cargar mis datos»
          }
        }
      }
      setHydrated(true);
    })();
  }, []);

  // Guarda la conversación cada vez que cambia.
  useEffect(() => {
    if (!hydrated) return;
    saveChatHistory({ profileLoaded, messages }).catch(() => {});
  }, [hydrated, profileLoaded, messages]);

  // Si el perfil o la rutina cambiaron desde que se cargó el contexto, se actualiza solo.
  useFocusEffect(
    useCallback(() => {
      if (!profileLoaded || !isProfileContextStale()) return;
      loadProfileContext()
        .then(() => {
          setSuggestions(getSuggestions());
          push({ from: "system", text: "Actualicé tus datos: ahora conozco tus últimos cambios." });
        })
        .catch(() => {});
    }, [profileLoaded]),
  );

  async function loadProfile(): Promise<boolean> {
    if (profileLoaded) return true;
    if (isLoadingProfile) return false;
    setIsLoadingProfile(true);
    try {
      await loadProfileContext();
      setProfileLoaded(true);
      setSuggestions(getSuggestions());
      push({
        from: "system",
        text: `Perfil de ${user?.name ?? "tu cuenta"} cargado — las respuestas ahora consideran tus objetivos, entorno y condiciones.`,
      });
      toast.success("Perfil cargado en la conversación");
      return true;
    } catch (error) {
      toast.error(extractErrorMessage(error));
      return false;
    } finally {
      setIsLoadingProfile(false);
    }
  }

  async function send(text: string, forceProfile = false) {
    const question = text.trim();
    if (!question || isSending) return;

    setInput("");
    push({ from: "user", text: question });
    setIsSending(true);
    try {
      const response = await askAssistant(question, forceProfile || profileLoaded);
      const { text: reply, actions } = parseReply(response.reply);
      push({ from: "bot", text: reply, personalized: response.personalized, actions: actions.length ? actions : undefined });
    } catch (error) {
      push({ from: "bot", text: `No pude responder: ${extractErrorMessage(error)}` });
    } finally {
      setIsSending(false);
    }
  }

  // Preguntas que llegan desde otras pantallas (p. ej. «Preguntar sobre este ejercicio»).
  useEffect(() => {
    if (!hydrated || !params.prompt) return;
    const key = `${params.prompt}|${params.t ?? ""}`;
    if (handledPrompt.current === key) return;
    handledPrompt.current = key;
    (async () => {
      const ok = await loadProfile();
      await send(String(params.prompt), ok);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, params.prompt, params.t]);

  async function applyAction(messageId: number, action: ChatAction) {
    try {
      const result = await runAction(action);
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, actions: m.actions?.filter((a) => a !== action) } : m)));
      push({ from: "system", text: result });
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }

  async function newConversation() {
    const ok = await confirm({
      title: "¿Empezar una conversación nueva?",
      message: "El asistente olvidará lo hablado hasta ahora.",
      confirmText: "Empezar de nuevo",
    });
    if (!ok) return;
    await startNewConversation();
    nextId.current = 1;
    setMessages([welcomeMessage()]);
    setProfileLoaded(false);
    setSuggestions(BASE_SUGGESTIONS);
  }

  return (
    <KeyboardAvoidingView style={styles.bg} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <GradientHeader style={{ paddingBottom: 16 }}>
        <View style={styles.headRow}>
          <View style={styles.avatarBot}>
            <Text style={{ fontSize: 22 }}>🤖</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.headName}>Asistente IA</Text>
            <Text style={styles.headStatus}>● En línea · Asistente de fitness</Text>
          </View>
          <PressableScale style={styles.newBtn} onPress={newConversation}>
            <Text style={styles.newBtnText}>✨ Nueva</Text>
          </PressableScale>
        </View>

        <PressableScale style={[styles.loadBtn, profileLoaded && styles.loadBtnDone]} onPress={loadProfile} disabled={profileLoaded || isLoadingProfile}>
          <View style={styles.loadBtnIcon}>
            <Text>📋</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.loadBtnTitle}>
              {profileLoaded ? "✅ Perfil cargado en la conversación" : isLoadingProfile ? "Cargando tus datos..." : "Cargar mis datos a la conversación"}
            </Text>
            <Text style={styles.loadBtnSub}>{profileLoaded ? (user?.name ?? "") : "Personaliza las respuestas con tu perfil"}</Text>
          </View>
        </PressableScale>
      </GradientHeader>

      <ScrollView
        ref={scrollRef}
        style={{ flex: 1 }}
        contentContainerStyle={styles.messages}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
        keyboardShouldPersistTaps="handled"
      >
        {messages.map((m) =>
          m.from === "system" ? (
            <Animated.View key={m.id} entering={FadeInDown.duration(300)} style={styles.systemRow}>
              <View style={styles.systemBubble}>
                <Text style={styles.systemText}>✅ {m.text}</Text>
              </View>
            </Animated.View>
          ) : (
            <Animated.View key={m.id} entering={FadeInDown.duration(300)} style={[styles.msgRow, m.from === "user" && styles.msgRowUser]}>
              <View style={[styles.msgAvatar, m.from === "bot" ? styles.msgAvatarBot : styles.msgAvatarUser]}>
                <Text style={styles.msgAvatarText}>{m.from === "bot" ? "🤖" : initials}</Text>
              </View>
              <View style={{ maxWidth: "78%" }}>
                <View style={[styles.bubble, m.from === "bot" ? styles.bubbleBot : styles.bubbleUser, m.personalized && styles.bubblePersonalized]}>
                  {m.from === "bot" ? <Markdown text={m.text} style={styles.bubbleTextBot} /> : <Text style={styles.bubbleTextUser}>{m.text}</Text>}
                </View>
                {m.actions?.length ? (
                  <View style={styles.actionsWrap}>
                    {m.actions.map((a, idx) => (
                      <PressableScale key={idx} style={styles.actionBtn} onPress={() => applyAction(m.id, a)}>
                        <Text>⚡</Text>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.actionText}>{describeAction(a)}</Text>
                          <Text style={styles.actionHint}>Toca para confirmar</Text>
                        </View>
                      </PressableScale>
                    ))}
                  </View>
                ) : null}
                <Text style={[styles.time, m.from === "user" && { textAlign: "right" }]}>{m.time}</Text>
              </View>
            </Animated.View>
          ),
        )}
        {isSending ? (
          <Animated.View entering={FadeInDown.duration(200)} style={styles.msgRow}>
            <View style={[styles.msgAvatar, styles.msgAvatarBot]}>
              <Text style={styles.msgAvatarText}>🤖</Text>
            </View>
            <View style={[styles.bubble, styles.bubbleBot]}>
              <TypingDots />
            </View>
          </Animated.View>
        ) : null}
      </ScrollView>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.suggestions}
        contentContainerStyle={{ paddingHorizontal: 12, gap: 7 }}
        keyboardShouldPersistTaps="handled"
      >
        {suggestions.map((s) => (
          <PressableScale key={s} style={styles.sug} onPress={() => send(s)}>
            <Text style={styles.sugText}>{s}</Text>
          </PressableScale>
        ))}
      </ScrollView>

      <View style={styles.inputWrap}>
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
        >
          <Text style={styles.sendIcon}>➤</Text>
        </Pressable>
      </View>
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
  loadBtn: {
    marginTop: 14,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 10,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.3)",
    borderRadius: 14,
    paddingVertical: 9,
    paddingHorizontal: 14,
  },
  loadBtnDone: { backgroundColor: "rgba(105,240,174,0.2)", borderColor: "rgba(105,240,174,0.6)" },
  loadBtnIcon: { width: 30, height: 30, borderRadius: 9, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  loadBtnTitle: { color: "#fff", fontSize: 13, fontWeight: "600" as const },
  loadBtnSub: { color: "rgba(255,255,255,0.75)", fontSize: 11 },
  messages: { padding: 12, gap: 12 },
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
  actionBtn: { flexDirection: "row" as const, alignItems: "center" as const, gap: 8, paddingVertical: 9, paddingHorizontal: 12, borderRadius: 12, borderWidth: 1, borderColor: colors.primary, backgroundColor: colors.primarySoft },
  actionText: { color: colors.primary, fontSize: 12, fontWeight: "600" as const },
  actionHint: { color: colors.textFaint, fontSize: 10, marginTop: 2 },
  systemRow: { alignItems: "center" as const },
  systemBubble: { backgroundColor: colors.primarySoft, borderRadius: 12, paddingVertical: 10, paddingHorizontal: 12, maxWidth: 300 },
  systemText: { color: colors.success, fontSize: 12 },
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
  sendBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.primary, alignItems: "center" as const, justifyContent: "center" as const },
  sendBtnDisabled: { opacity: 0.45 },
  sendIcon: { color: "#fff", fontSize: 16 },
});
