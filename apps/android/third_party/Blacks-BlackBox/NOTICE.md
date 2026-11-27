# Blacks BlackBox

This project vendors the `Bcore`, `black-reflection`, and `compiler` modules from:

- Project: Blacks BlackBox
- Repository: https://github.com/Black00Z/Blacks-BlackBox
- Commit: `a13734339f85a85b4400926f7142557cb6f97dd9`
- License: Apache License 2.0, see `LICENSE`

Local changes include Android Gradle build compatibility, Android API 37 build fixes,
and a corrected APK/APKS detection rule that preserves nested SDK APK assets inside
regular game APKs. Proxy startup also initializes the guest Application explicitly on
newer Android versions when the framework callback binds it too late. A duplicate
case-variant inner hook class was removed because D8 collapsed it and broke reflective
Hook registration.
