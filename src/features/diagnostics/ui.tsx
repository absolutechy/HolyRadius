import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';

export function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

export function Button({ label, onPress, disabled }: { label: string; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [styles.button, pressed && styles.pressed, disabled && styles.disabled]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

export function Row({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}

export function KV({ k, v }: { k: string; v: unknown }) {
  const text = typeof v === 'string' ? v : JSON.stringify(v);
  return (
    <Text style={styles.kv}>
      <Text style={styles.k}>{k}: </Text>
      {text}
    </Text>
  );
}

export function Field(props: TextInputProps & { label: string }) {
  const { label, style, ...rest } = props;
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput {...rest} style={[styles.input, style]} autoCapitalize="none" autoCorrect={false} />
    </View>
  );
}

export function Mono({ children }: { children: ReactNode }) {
  return <Text style={styles.mono}>{children}</Text>;
}

export const styles = StyleSheet.create({
  section: { backgroundColor: '#fff', borderRadius: 8, padding: 12, marginBottom: 12, gap: 6 },
  sectionTitle: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  button: { backgroundColor: '#1f6f5c', paddingVertical: 8, paddingHorizontal: 12, borderRadius: 6 },
  pressed: { opacity: 0.7 },
  disabled: { backgroundColor: '#9aa' },
  buttonText: { color: '#fff', fontWeight: '600' },
  kv: { fontSize: 13 },
  k: { fontWeight: '600' },
  field: { flexGrow: 1, minWidth: 90 },
  fieldLabel: { fontSize: 12, color: '#555' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 6, paddingHorizontal: 8, paddingVertical: 4 },
  mono: { fontFamily: 'monospace', fontSize: 11 },
  error: { color: '#b00020' },
  note: { fontSize: 12, color: '#555' },
});
