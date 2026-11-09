import React from "react";
import { Text, View } from "react-native";

import { formatNumber, humanize, weekdayName } from "../../constants/labels";
import { AccountView } from "../../services/types";
import { useTheme } from "../../theme";

/** Resumen del perfil común (edad, IMC, medidas, disponibilidad y entornos). */
export default function ProfileSummary({ account }: { account: AccountView }) {
  const { colors, formatWeight, formatHeight } = useTheme();
  const p = account.profile;
  const stats = [
    { label: "Edad", value: account.age != null ? `${account.age}` : "—" },
    { label: "Peso", value: formatWeight(p.weightKg) },
    { label: "Estatura", value: formatHeight(p.heightCm) },
    { label: "IMC", value: account.bmi != null ? formatNumber(account.bmi) : "—" },
  ];
  const availability = [
    p.daysPerWeek ? `${p.daysPerWeek} días/semana` : null,
    p.minutesPerSession ? `${p.minutesPerSession} min por sesión` : null,
  ].filter(Boolean);
  const days = p.preferredDays.map((d) => weekdayName(d, true)).join(", ");
  const places = p.environments.map(humanize).join(", ");

  return (
    <View style={{ gap: 12 }}>
      <View style={{ flexDirection: "row", gap: 8 }}>
        {stats.map((s) => (
          <View key={s.label} style={{ flex: 1, backgroundColor: colors.cardAlt, borderRadius: 12, padding: 10, alignItems: "center" }}>
            <Text style={{ fontSize: 15, fontWeight: "800", color: colors.text }}>{s.value}</Text>
            <Text style={{ fontSize: 10, color: colors.textFaint, marginTop: 2 }}>{s.label}</Text>
          </View>
        ))}
      </View>
      {availability.length ? <Line label="Disponibilidad" value={availability.join(" · ")} /> : null}
      {days ? <Line label="Días preferidos" value={days} /> : null}
      {places ? <Line label="Dónde entrenas" value={places} /> : null}
    </View>
  );
}

function Line({ label, value }: { label: string; value: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: 8 }}>
      <Text style={{ fontSize: 12, color: colors.textFaint, width: 110 }}>{label}</Text>
      <Text style={{ flex: 1, fontSize: 12, color: colors.text, fontWeight: "600" }}>{value}</Text>
    </View>
  );
}
