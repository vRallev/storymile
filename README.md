# Storymile

[![CI](https://github.com/vRallev/storymile/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/vRallev/storymile/actions/workflows/ci.yml)

<img src="images/icon.png" alt="Storymile" width="150" align="left" />

A podcast app for Android, Desktop, iOS, and web, with support for custom generated
episodes in your library. Built with Kotlin Multiplatform and
[App Platform](https://github.com/vRallev/app-platform).

<br clear="left" />

Try the web app and read the presenter hierarchy on the
[Storymile site](https://vrallev.github.io/storymile/).

## Try Google login

The prototype uses Supabase Auth. Open **Settings → Account** to sign in. Guest listening works
without configuration. Signing in opens a separate local library for the verified account. Signing
out, restarting the app, or reloading the web page returns to the guest. Signing in again reopens
the account's local library. Libraries do not sync between devices.

1. Create a development Supabase project. In Google Cloud, create a Web OAuth client. Register
   `https://<project-ref>.supabase.co/auth/v1/callback` as an authorized redirect URI. Enable
   Google in Supabase Auth and enter the Web client ID and secret there. If the Google consent
   screen is in testing, add the Google accounts that will try the prototype.
2. Add the public build settings to `~/.gradle/gradle.properties`:

   ```properties
   storymile.auth.supabaseUrl=https://<project-ref>.supabase.co
   storymile.auth.publishableKey=sb_publishable_<key>
   storymile.auth.googleWebClientId=<web-client-id>.apps.googleusercontent.com
   ```

3. Configure the platforms you want to try:

   | Platform | Setup |
   | --- | --- |
   | Android | In the same Google project, create an Android OAuth client for `software.ralf.storymile` and the SHA-1 of the signing certificate. Use a device with Google Play services. |
   | iOS | Create an iOS OAuth client for the built app's bundle ID. Set the Xcode build setting `STORYMILE_GOOGLE_IOS_CLIENT_ID_PREFIX` to the part before `.apps.googleusercontent.com` in its client ID. Include the iOS client ID among the accepted Google client IDs in Supabase, with the Web ID first. |
   | Desktop | In Supabase Auth → URL Configuration, add `http://127.0.0.1:*/google-login/*` to the redirect allowlist. The app opens the system browser and receives the result on a temporary loopback port. |
   | Web | Allowlist the exact standalone app URL, including the path and trailing slash or `index.html`. Use HTTPS or localhost. For a deployed build, supply the public Gradle settings when building the web distribution. |

4. Rebuild and launch with `./run.sh`. Sign in, save a show, sign out, and sign back in to check
   which library opens. [Google provider setup](https://supabase.com/docs/guides/auth/social-sign-in/auth-google)

## Contributing

See [AGENTS.md](AGENTS.md) for project structure, setup, and testing.

## License

Copyright 2026 Ralf Wondratschek.

Licensed under the [Apache License 2.0](LICENSE).
