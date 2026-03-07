<a href="https://github.com/Trooped/QuickBars/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Trooped/QuickBars" />
</a>

I am happy to review contributions! To ensure a smooth process and avoid wasted effort, please follow these steps:

1.  **Open an Issue**: Before writing any code, [open a new issue](https://github.com/Trooped/QuickBars/issues/new) to describe the bug or feature.
2.  **Discussion**: Wait for other people to chime in and discuss the issue, and then for me to approve that we should start working on it. This ensures the change aligns with the project goals and architecture.
3.  **Fork & Branch**: Once the issue is approved, fork the repo and create your feature branch.
4.  **Run in Android Studio**: Open the project, synchronize Gradle files, and make your changes.
5.  **Local Testing**: Test your changes on a real Android TV using a **Debug Build**. Ensure your change does not impact existing features.
6.  **Pull Request**: Submit your PR referencing the original issue.

---

### :running: Run The Debug Variant For Testing

You do not need my private signing keys or API tokens to test it while contributing. To get a build running:

1.  **Setup Global Properties**: Create or edit your global `gradle.properties` file:
    * **Windows**: `C:\Users\<YourName>\.gradle\gradle.properties`
    * **Mac/Linux**: `~/.gradle/gradle.properties`
2.  **Add Placeholder Variables**: Add the following lines so the build script doesn't fail:
    ```properties
    REVENUECAT_API_KEY="dummy_key"
    HA_QUICKBARS_KEYSTORE_FILE="not_needed_for_debug"
    HA_QUICKBARS_KEYSTORE_PASSWORD="dummy"
    HA_QUICKBARS_KEY_ALIAS="dummy"
    HA_QUICKBARS_KEY_PASSWORD="dummy"
    ```
3.  **Build the Debug Variant**: In Android Studio, ensure the **Build Variant** is set to `debug`. This uses the standard Android debug key and will allow the app to run on your TV.
