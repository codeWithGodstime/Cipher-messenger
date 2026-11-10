# Mobile app

Android app in this directory (`mobile/`). Build it from Android Studio.

## Do not build after updates

After changing source, Gradle files, dependencies, or resources, do not run a build.

Do not run Gradle unless the user explicitly asks. That includes:

- `./gradlew assembleDebug`, `assembleRelease`, `build`, `installDebug`, `bundle`
- any other `./gradlew` task
- Android Studio build or install actions

Leave compilation to the user.
