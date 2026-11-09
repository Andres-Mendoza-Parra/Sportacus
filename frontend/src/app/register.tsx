import { Link, router } from "expo-router";
import { useState } from "react";
import { Pressable, Text } from "react-native";

import AuthShell from "../components/AuthShell";
import GradientButton from "../components/ui/GradientButton";
import { useToast } from "../components/ui/Feedback";
import TextField from "../components/ui/TextField";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../services/api";
import { useTheme } from "../theme";

type Errors = Partial<Record<"name" | "email" | "password" | "confirm", string>>;

export default function RegisterScreen() {
  const { register } = useAuth();
  const { colors } = useTheme();
  const toast = useToast();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  function clear(field: keyof Errors) {
    if (errors[field]) setErrors((e) => ({ ...e, [field]: undefined }));
  }

  async function handleSubmit() {
    const next: Errors = {};
    if (!name.trim()) next.name = "Cuéntanos cómo te llamas";
    if (!email.trim()) next.email = "Ingresa tu correo";
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = "El correo no parece válido";
    if (password.length < 6) next.password = "Debe tener al menos 6 caracteres";
    if (confirmPassword !== password) next.confirm = "Las contraseñas no coinciden";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setIsSubmitting(true);
    try {
      await register(name.trim(), email.trim(), password);
      toast.success("¡Cuenta creada! Empecemos por conocerte.");
      router.replace("/onboarding");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthShell subtitle="Crea tu cuenta para empezar">
      <Text style={{ fontSize: 18, fontWeight: "700", color: colors.primaryDark, marginBottom: 18 }}>Registrarse</Text>

      <TextField
        label="Nombre"
        placeholder="Juan García"
        autoCapitalize="words"
        maxLength={80}
        value={name}
        onChangeText={(v) => {
          setName(v);
          clear("name");
        }}
        error={errors.name}
      />
      <TextField
        label="Correo electrónico"
        placeholder="tu@correo.com"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={(v) => {
          setEmail(v);
          clear("email");
        }}
        error={errors.email}
      />
      <TextField
        label="Contraseña"
        placeholder="Mínimo 6 caracteres"
        passwordToggle
        value={password}
        onChangeText={(v) => {
          setPassword(v);
          clear("password");
        }}
        error={errors.password}
      />
      <TextField
        label="Confirmar contraseña"
        placeholder="••••••••"
        passwordToggle
        value={confirmPassword}
        onChangeText={(v) => {
          setConfirmPassword(v);
          clear("confirm");
        }}
        onSubmitEditing={handleSubmit}
        error={errors.confirm}
      />

      <GradientButton title="Crear cuenta" onPress={handleSubmit} loading={isSubmitting} style={{ marginTop: 6 }} />

      <Link href="/login" asChild>
        <Pressable style={{ marginTop: 18, alignItems: "center" }}>
          <Text style={{ color: colors.textMuted, fontSize: 13 }}>
            ¿Ya tienes cuenta? <Text style={{ color: colors.primary, fontWeight: "700" }}>Inicia sesión</Text>
          </Text>
        </Pressable>
      </Link>
    </AuthShell>
  );
}
