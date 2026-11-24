# Fix for GitHub Actions build error

The workflow now runs the Android app module explicitly with `:app:assembleDebug` and checks that the Android project files are in the repository root before Gradle starts.

## Required repository layout

The repository root must contain these entries directly:

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/`
- `.github/workflows/android.yml`

Inside `app/`, keep `build.gradle.kts` and `src/main/AndroidManifest.xml`.

Do not upload an enclosing `HBrowserNext/` or `H-Browser-V3/` folder as the only item in the repository. Open the extracted folder and upload its contents so `settings.gradle.kts` is visible at the top level on GitHub.

## Run again

Open GitHub → Actions → Build H Browser → Run workflow. The artifact is named `H-Browser-V3-debug`.
