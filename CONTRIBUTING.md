# Contributing to QuickBars

First off, thank you for considering contributing to QuickBars!

Please take a moment to review these guidelines to make the contribution process effective for everyone.

## ⚠️ Rules of Engagement
**To keep this project manageable for a solo developer/student, please adhere to these strict rules:**
* **Language:** All issues, pull requests, and discussions must be in **English**.
* **Completeness:** You must fill out **all sections** of the provided templates.
* **Automatic Closure:** Issues or discussions that are not in English or are incomplete will be closed immediately.
* **Respect the Maintainer:** This project is managed by Omri in his spare time. Help me help you by being clear and professional.

---

## :octocat: The Contribution Workflow
To ensure that changes align with the project's goals and architecture, please follow our refined workflow:

1. **Start a Discussion**: Before writing any code, [start a new discussion](https://github.com/Trooped/QuickBars/discussions/new/choose). 
   - Use the **Ideas** category for new features.
   - Use the **Q&A** category if you aren't sure if something is a bug or just a configuration issue.
2. **Consensus & Approval**: Wait for the community and maintainers to chime in. Once we agree on the approach, a maintainer will convert the discussion into a formal **Issue**.
3. **Fork & Branch**: Once the official issue is created and assigned, fork the repository. Create a new descriptive branch (e.g., `feature/new-entity-support` or `fix/overlay-flicker`).
4. **Local Development**: Set up your environment (see [Local Setup](#toolbox-local-setup) below).
5. **Manual Testing**: Verify your changes on a real device (see [Testing](#test_tube-testing--quality-assurance) below).
6. **Submit a Pull Request**: Submit your PR referencing the original issue (e.g., "Fixes #12"). Please write clear, descriptive commit messages.

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
To keep the codebase clean and readable, please ensure your code follows standard Android Kotlin styling conventions. Before submitting a PR, run the standard Android Studio auto-formatter (Ctrl+Alt+L on Windows, Cmd+Option+L on Mac) on any files you have modified.

## :test_tube: Testing & Quality Assurance
Because QuickBars interacts with system-level overlays and physical remote key interception, testing is currently a manual process.

**The Current Testing Standard:**

**Hardware**: We test on real Android TV hardware, such as the Nvidia Shield or Chromecast with Google TV, to ensure performance and compatibility.

**Manual Checks**: We make sure the feature/fix we implemented is working as expected, and that any other feature it may have affected was not affected at all. This requires familiarity with the codebase.
**If large changes were made, especially ones that affect data storage and persistance** - then the following test is required:
1. Run the main (public version) branch on your Android TV device using a debug build, and set it up normally (connect to HA, create a QuickBar with some entities).
2. Run your new branch on the same device (simulating a software update).
3. Make sure no data was corrupted during this update, and test all other functionality normally.

## :brain: AI Use in Contributions
AI use in PRs is allowed, but all code AI writes must be 100% understandable and familiar to the person making the PR, as if he wrote it himself, as well as meet the project's code and testing standards.

## :rocket: Help Wanted: CI/CD & Automation
Manual testing is the current standard for this project, but I am very open to improvements. If you have experience with automated UI testing for Android TV or ideas for a CI/CD pipeline, **please start a thread in [Discussions](https://github.com/Trooped/QuickBars/discussions/categories/Ideas)!** I would love to discuss how to make the project more robust.

## :scroll: License
By contributing to QuickBars, you agree that your contributions will be licensed under the GNU General Public License v3.0 (GPLv3).
