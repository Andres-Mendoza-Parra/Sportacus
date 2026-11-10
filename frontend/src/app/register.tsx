import { Link, router } from "expo-router";
import { useState } from "react";
import { Pressable, Text } from "react-native";

import AuthShell from "../components/AuthShell";
import CheckRow from "../components/ui/CheckRow";
import GradientButton from "../components/ui/GradientButton";
import { useToast } from "../components/ui/Feedback";
import NoticeBox from "../components/ui/NoticeBox";
import TextField from "../components/ui/TextField";
import { ADULT_AGE, MIN_AGE, PASSWORD_HINT, PASSWORD_MAX, ageFromBirthYear, currentYear, passwordError } from "../constants/validation";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../services/api";
import { useTheme } from "../theme";

type Field = "name" | "email" | "birthYear" | "password" | "confirm" | "policy" | "guardian";
type Errors = Partial<Record<Field, string>>;

/**
 * Registro: datos básicos, año de nacimiento (edad mínima 12; de 12 a 17 con autorización de madre,
 * padre o tutor), aceptación obligatoria de la Política de datos y los Términos, y consentimiento
 * opcional para datos de salud (Ley 1581 de 2012 y Decreto 1377 de 2013).
 */
export default function RegisterScreen() {
  const { register } = useAuth();
  const { colors } = useTheme();
  const toast = useToast();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [birthYear, setBirthYear] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [acceptPolicy, setAcceptPolicy] = useState(false);
  const [acceptHealth, setAcceptHealth] = useState(false);
  const [guardian, setGuardian] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const year = /^\d{4}$/.test(birthYear) ? Number(birthYear) : null;
  const age = year != null && year <= currentYear() ? ageFromBirthYear(year) : null;
  const tooYoung = age != null && age < MIN_AGE;
  const minor = age != null && age >= MIN_AGE && age < ADULT_AGE;

  function clear(field: Field) {
    if (errors[field]) setErrors((e) => ({ ...e, [field]: undefined }));
  }

  async function handleSubmit() {
    const next: Errors = {};
    if (!name.trim()) next.name = "Cuéntanos cómo te llamas";
    if (!email.trim()) next.email = "Ingresa tu correo";
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = "El correo no parece válido";
    if (!birthYear.trim()) next.birthYear = "Ingresa tu año de nacimiento";
    else if (year == null || year > currentYear() || year < currentYear() - 110) next.birthYear = "Escribe un año válido, por ejemplo 2001";
    const pwd = passwordError(password);
    if (pwd) next.password = pwd;
    if (confirmPassword !== password) next.confirm = "Las contraseñas no coinciden";
    if (!acceptPolicy) next.policy = "Para crear tu cuenta necesitas aceptar la Política y los Términos";
    if (minor && !guardian) next.guardian = "Necesitamos que una persona adulta responsable conozca y autorice tu uso de Sportacus";
    setErrors(next);
    if (Object.keys(next).length > 0 || tooYoung || year == null) return;

    setIsSubmitting(true);
    try {
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
        birthYear: year,
        acceptPolicy: true,
        acceptHealthData: acceptHealth,
        ...(minor ? { guardianConsent: true } : {}),
      });
      toast.success("¡Te damos la bienvenida! Esta es tu Base: empieza cuando quieras.");
      router.replace("/home");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  const link = { color: colors.primary, fontWeight: "700" as const, textDecorationLine: "underline" as const };

  return (
    <AuthShell subtitle="Crea tu cuenta para empezar">
      <Text style={{ fontSize: 18, fontWeight: "700", color: colors.primaryDark, marginBottom: 18 }}>Crear cuenta</Text>

      <TextField
        label="Nombre"
        placeholder="Tu nombre"
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
        label="Año de nacimiento"
        placeholder={`Por ejemplo, ${currentYear() - 25}`}
        keyboardType="number-pad"
        maxLength={4}
        value={birthYear}
        onChangeText={(v) => {
          setBirthYear(v.replace(/\D/g, ""));
          clear("birthYear");
          clear("guardian");
        }}
        error={errors.birthYear}
      />
      {tooYoung ? (
        <NoticeBox
          tone="info"
          icon="🌱"
          title="¡Gracias por tu interés!"
          body={`Sportacus es para personas de ${MIN_AGE} años o más. Mientras tanto, moverte jugando con tu familia y tus amistades es la mejor forma de entrenar. ¡Te esperamos cuando cumplas ${MIN_AGE}!`}
          style={{ marginBottom: 14 }}
        />
      ) : null}
      <TextField
        label="Contraseña"
        placeholder={PASSWORD_HINT}
        passwordToggle
        maxLength={PASSWORD_MAX}
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
        maxLength={PASSWORD_MAX}
        value={confirmPassword}
        onChangeText={(v) => {
          setConfirmPassword(v);
          clear("confirm");
        }}
        onSubmitEditing={handleSubmit}
        error={errors.confirm}
      />

      <CheckRow
        checked={acceptPolicy}
        onChange={(v) => {
          setAcceptPolicy(v);
          clear("policy");
        }}
        label="Acepto la Política de tratamiento de datos y los Términos"
        required
        error={errors.policy}
      >
        <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>
          Acepto la{" "}
          <Text style={link} onPress={() => router.push("/legal/politica")} accessibilityRole="link">
            Política de tratamiento de datos
          </Text>{" "}
          y los{" "}
          <Text style={link} onPress={() => router.push("/legal/terminos")} accessibilityRole="link">
            Términos
          </Text>
          .
        </Text>
      </CheckRow>
      <CheckRow checked={acceptHealth} onChange={setAcceptHealth} label="Autorizo el tratamiento de mis datos de salud (opcional)">
        <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>
          Autorizo el tratamiento de mis datos de salud{" "}
          <Text style={{ color: colors.textMuted }}>(opcional, puedes darla después). Los usamos solo para cuidarte en tu plan.</Text>
        </Text>
      </CheckRow>
      {minor ? (
        <CheckRow
          checked={guardian}
          onChange={(v) => {
            setGuardian(v);
            clear("guardian");
          }}
          label="Mi madre, padre o tutor conoce y autoriza que use Sportacus"
          required
          error={errors.guardian}
        >
          <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>
            Mi madre, padre o tutor conoce y autoriza que use Sportacus.
          </Text>
        </CheckRow>
      ) : null}

      <GradientButton title="Crear cuenta" onPress={handleSubmit} loading={isSubmitting} disabled={tooYoung} style={{ marginTop: 6 }} />

      <Link href="/login" asChild>
        <Pressable style={{ marginTop: 10, minHeight: 44, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ color: colors.textMuted, fontSize: 13 }}>
            ¿Ya tienes cuenta? <Text style={{ color: colors.primary, fontWeight: "700" }}>Inicia sesión</Text>
          </Text>
        </Pressable>
      </Link>
    </AuthShell>
  );
}
