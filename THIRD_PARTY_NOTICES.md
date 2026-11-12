# Third-party notices

MyBambuddy is licensed under the Apache License 2.0 (`LICENSE`).
The app bundles open-source libraries and a few Google components under their own terms, grouped below by the license each library's published build file (POM) declares in the app's release runtime classpath.
This is a summary, not legal advice: it reports what the POMs declare and was not checked against each library's own license files.

| License as declared | Notes |
|---|---|
| Apache License 2.0 (several spellings) | AndroidX, Jetpack Compose, Kotlin, kotlinx, OkHttp, Retrofit, Moshi, Coil, Room and others; almost all libraries |
| Android Software Development Kit License | Google Play services base, basement, tasks and location, and `odml image`; closed-source components under Google's terms |
| ML Kit Terms of Service | Google ML Kit (code scanner, barcode scanning, vision common); Google's terms apply |
| BSD-3-Clause | for example `datastore-preferences-external-protobuf` |
| MIT | `org.checkerframework:checker-qual` |

## Notes

- The Google Play services and ML Kit components are not open source.
- Barcode scanning uses Google's code scanner through Google Play services, so it needs a device with Google Play services.
- Test-only libraries (JUnit, MockWebServer, Compose test) are not shipped in the app and are not listed.
- Bambuddy and Spoolman are separate programs the app talks to over HTTP.
  No code from them is included by design; this was not verified by a code comparison.
