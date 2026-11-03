import React, { useState } from "react";
import { Pressable, StyleProp, Text, TextInput, TextInputProps, View, ViewStyle } from "react-native";
import Animated, { FadeIn, FadeOut } from "react-native-reanimated";

import { useTheme } from "../../theme";

interface Props extends TextInputProps {
  label: string;
  error?: string | null;
  /** Muestra el botón 👁 para ver/ocultar el texto (campos de contraseña). */
  passwordToggle?: boolean;
  containerStyle?: StyleProp<ViewStyle>;
}

/** Campo de texto con etiqueta, foco resaltado, error en línea y toggle de contraseña. */
export default function TextField({ label, error, passwordToggle, containerStyle, style, onFocus, onBlur, ...rest }: Props) {
  const { colors } = useTheme();
  const [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(true);

  return (
    <View style={[{ marginBottom: 14 }, containerStyle]}>
      <Text style={{ fontSize: 12, color: colors.textMuted, marginBottom: 4, fontWeight: "500" }}>{label}</Text>
      <View style={{ justifyContent: "center" }}>
        <TextInput
          {...rest}
          secureTextEntry={passwordToggle ? hidden : rest.secureTextEntry}
          placeholderTextColor={colors.textFaint}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          style={[
            {
              paddingVertical: 11,
              paddingHorizontal: 14,
              paddingRight: passwordToggle ? 44 : 14,
              borderWidth: 1.5,
              borderColor: error ? colors.danger : focused ? colors.primary : colors.border,
              borderRadius: 12,
              fontSize: 14,
              backgroundColor: colors.inputBg,
              color: colors.text,
            },
            style,
          ]}
        />
        {passwordToggle ? (
          <Pressable
            onPress={() => setHidden((h) => !h)}
            hitSlop={8}
            style={{ position: "absolute", right: 12 }}
            accessibilityLabel={hidden ? "Mostrar contraseña" : "Ocultar contraseña"}
          >
            <Text style={{ fontSize: 16, opacity: 0.7 }}>{hidden ? "👁️" : "🙈"}</Text>
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <Animated.Text
          entering={FadeIn.duration(150)}
          exiting={FadeOut.duration(100)}
          style={{ color: colors.danger, fontSize: 12, marginTop: 4 }}
        >
          {error}
        </Animated.Text>
      ) : null}
    </View>
  );
}
