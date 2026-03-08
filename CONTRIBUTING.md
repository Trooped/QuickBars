# Contributing to QuickBars

First off, thank you for considering contributing to QuickBars!

Please take a moment to review these guidelines to make the contribution process effective for everyone.

## :octocat: The Contribution Workflow

To ensure that changes align with the project's goals and architecture, please follow this "Issue-First" workflow:

1.  **Open an Issue**: Before writing any code, [open a new issue](https://github.com/Trooped/QuickBars/issues/new) to describe the bug or feature.
2.  **Discussion**: Wait for other people to chime in and discuss the issue, and then for me to approve that we should start working on it. This ensures the change aligns with the project goals and architecture.
3.  **Fork & Branch**: Once the issue is approved, fork the repository. Create a new descriptive feature branch from the main branch (e.g., `feature/new-entity-support` or `fix/overlay-flicker`).
4.  **Local Development**: Set up your environment (see [Local Setup](#toolbox-local-setup) below).
5.  **Manual Testing**: Verify your changes on a real device (see [Testing](#test_tube-testing--quality-assurance) below).
6.  **Submit a Pull Request**: Submit your PR referencing the original issue (e.g., "Fixes #12"). Please write clear, descriptive commit messages so the project history remains easy to read.

## :toolbox: Local Setup

You can build and test QuickBars without access to production API keys or private signing certificates.

### 1. Setup Global Properties
To prevent the build from failing, you must provide placeholder values for the required variables in your **global** `gradle.properties` file.

* **Windows**: `C:\Users\<YourName>\.gradle\gradle.properties`
* **Mac/Linux**: `~/.gradle/gradle.properties`

Add the following lines to that file:
```properties
REVENUECAT_API_KEY="dummy_key"
HA_QUICKBARS_KEYSTORE_FILE="not_needed_for_debug"
HA_QUICKBARS_KEYSTORE_PASSWORD="dummy"
HA_QUICKBARS_KEY_ALIAS="dummy"
HA_QUICKBARS_KEY_PASSWORD="dummy"
```

### 2. Build Variant
In Android Studio, ensure you are using the debug build variant. This variant uses the standard Android debug key and ignores the production signing configuration.

## :art: Code Style
To keep the codebase clean and readable, please ensure your code follows standard [Android Kotlin styling conventions](https://developer.android.com/kotlin/style-guide). Before submitting a PR, run the standard Android Studio auto-formatter (Ctrl+Alt+L on Windows, Cmd+Option+L on Mac) on any files you have modified.

## :test_tube: Testing & Quality Assurance
Because QuickBars interacts with system-level overlays and physical remote key interception, testing is currently a manual process.

**The Current Testing Standard:**

**Hardware**: We test on real Android TV hardware, such as the Nvidia Shield or Chromecast with Google TV, to ensure performance and compatibility.

**Manual Checks**: We make sure the feature/fix we implemented is working as expected, and that any other feature it may have affected was not affected at all. This requires familiarity with the codebase.

## :rocket: Help Wanted: CI/CD & Automation
Manual testing is the current standard for this project, but I am very open to improvements. If you have experience with automated UI testing for Android TV or ideas for a CI/CD pipeline (e.g., GitHub Actions for build verification), please open an issue! I would love to discuss how to make the project more robust.

## :scroll: License
By contributing to QuickBars, you agree that your contributions will be licensed under the GNU General Public License v3.0 (GPLv3).
