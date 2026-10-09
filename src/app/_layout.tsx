import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerStyle: { backgroundColor: '#f2f4f3' } }}>
        <Stack.Screen name="index" options={{ title: 'HolyRadius · Phase 1 diagnostics' }} />
      </Stack>
    </>
  );
}
