# Storymile

[![CI](https://github.com/vRallev/storymile/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/vRallev/storymile/actions/workflows/ci.yml)

<img src="images/icon.png" alt="Storymile" width="150" align="left" />

A podcast app for Android, Desktop, iOS, and web, with support for custom generated
episodes in your library. Built with Kotlin Multiplatform and
[App Platform](https://github.com/vRallev/app-platform).

<br clear="left" />

Try the web app and read the presenter hierarchy on the
[Storymile site](https://vrallev.github.io/storymile/).

## Google sign-in

Enable Google in **Firebase Authentication → Sign-in method**. Add these public values to
`~/.gradle/gradle.properties`, then rebuild:

```properties
storymile.login.firebaseApiKey=<Firebase Web API key>
storymile.login.googleClientId=<Google Web OAuth client ID>
```

Register each target in the same Google Cloud project:

| Target | Configuration |
| --- | --- |
| Android | Register `software.ralf.storymile` and the signing certificate's SHA-1. |
| iOS | Register the built app's bundle ID. Set `STORYMILE_GOOGLE_IOS_CLIENT_ID_PREFIX` in Xcode to the part of the iOS client ID before `.apps.googleusercontent.com`. |
| Desktop | Download the **Desktop app** OAuth client JSON to `login/impl/src/desktopMain/resources/google-desktop-client.json`. Create the directory if needed. Allow that client ID in Firebase's Google provider settings. The installed-app configuration ships with the Desktop build. |
| Web | Add the app's origin (scheme, hostname, and port) to the Web OAuth client's authorized JavaScript origins. |

Open **Settings → Account** to sign in. Each account has a local library on the device.
Signing out, restarting the app, or reloading the page returns to the guest library.

## Contributing

See [AGENTS.md](AGENTS.md) for project structure, setup, and testing.

## License

Copyright 2026 Ralf Wondratschek.

Licensed under the [Apache License 2.0](LICENSE).
