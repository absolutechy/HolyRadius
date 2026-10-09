// App entry. Background task definitions must be evaluated before the router mounts so that
// headless (app-closed) task invocations can find them. Import order matters here.
import './src/features/taskmanager-baseline/register';
import 'expo-router/entry';
