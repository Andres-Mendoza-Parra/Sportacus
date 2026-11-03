import React from "react";
import { StyleProp, Text, TextStyle, View } from "react-native";

interface Props {
  text: string;
  style: StyleProp<TextStyle>;
}

const INLINE = /(\*\*[^*\n]+\*\*|__[^_\n]+__|\*[^*\s][^*\n]*\*|`[^`\n]+`)/g;

/** Texto en línea: **negrita**, *cursiva* y `código`. */
function renderInline(text: string, keyPrefix: string): React.ReactNode[] {
  return text.split(INLINE).filter(Boolean).map((part, i) => {
    const key = `${keyPrefix}-${i}`;
    if ((part.startsWith("**") && part.endsWith("**")) || (part.startsWith("__") && part.endsWith("__"))) {
      return (
        <Text key={key} style={{ fontWeight: "700" }}>
          {part.slice(2, -2)}
        </Text>
      );
    }
    if (part.startsWith("*") && part.endsWith("*") && part.length > 2) {
      return (
        <Text key={key} style={{ fontStyle: "italic" }}>
          {part.slice(1, -1)}
        </Text>
      );
    }
    if (part.startsWith("`") && part.endsWith("`")) {
      return (
        <Text key={key} style={{ fontFamily: "monospace" }}>
          {part.slice(1, -1)}
        </Text>
      );
    }
    return part;
  });
}

/**
 * Renderizador mínimo de Markdown para las respuestas del asistente:
 * negritas, cursivas, listas con viñetas o numeradas y encabezados.
 */
export default function Markdown({ text, style }: Props) {
  const lines = text.replace(/\r/g, "").split("\n");
  return (
    <View style={{ gap: 3 }}>
      {lines.map((line, i) => {
        const key = `l${i}`;
        if (line.trim() === "") return <View key={key} style={{ height: 4 }} />;

        const heading = /^\s{0,3}#{1,4}\s+(.*)$/.exec(line);
        if (heading) {
          return (
            <Text key={key} style={[style, { fontWeight: "700" }]}>
              {renderInline(heading[1], key)}
            </Text>
          );
        }

        const bullet = /^\s*[-*•]\s+(.*)$/.exec(line);
        if (bullet) {
          return (
            <View key={key} style={{ flexDirection: "row", gap: 6, paddingLeft: 4 }}>
              <Text style={style}>•</Text>
              <Text style={[style, { flex: 1 }]}>{renderInline(bullet[1], key)}</Text>
            </View>
          );
        }

        const ordered = /^\s*(\d+)[.)]\s+(.*)$/.exec(line);
        if (ordered) {
          return (
            <View key={key} style={{ flexDirection: "row", gap: 6, paddingLeft: 4 }}>
              <Text style={style}>{ordered[1]}.</Text>
              <Text style={[style, { flex: 1 }]}>{renderInline(ordered[2], key)}</Text>
            </View>
          );
        }

        return (
          <Text key={key} style={style}>
            {renderInline(line, key)}
          </Text>
        );
      })}
    </View>
  );
}
