import React from "react";
import { StyleProp, ViewStyle } from "react-native";

import { Card, CardTitle } from "../ui/Card";

interface Props {
  title?: string | null;
  subtitle?: string | null;
  right?: React.ReactNode;
  delay?: number;
  style?: StyleProp<ViewStyle>;
  children: React.ReactNode;
}

/** Marco común de los widgets: tarjeta con título y subtítulo del backend. */
export default function WidgetFrame({ title, subtitle, right, delay, style, children }: Props) {
  return (
    <Card delay={delay} style={style}>
      <CardTitle title={title} subtitle={subtitle} right={right} />
      {children}
    </Card>
  );
}
