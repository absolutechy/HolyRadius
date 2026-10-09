# native-tests

JUnit tests for the pure Kotlin decision logic in
`modules/holy-radius-native/android/src/main/java/com/holyradius/nativecore/{domain,config}`.

Those packages must not import `android.*`, so they compile on a plain JVM:

```
cd native-tests && gradle test
```
