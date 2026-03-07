<div align="center">

 <a href="https://github.com/Trooped/QuickBars">
        <img src="assets/icon.svg" alt="QuickBars App Screenshot" width="300"/>
  </a>
  <h1>QuickBars for Home Assistant</h1>
  
  <p>
    Home Assistant on your Android TV!
  </p>
  
  
<p>
  <a href="https://github.com/Trooped/QuickBars/graphs/contributors">
    <img src="https://img.shields.io/github/contributors/Trooped/QuickBars" alt="contributors" />
  </a>
  <a href="https://github.com/Trooped/QuickBars/commits/master">
    <img src="https://img.shields.io/github/last-commit/Trooped/QuickBars" alt="last update" />
  </a>
  <a href="https://github.com/Trooped/QuickBars/network/members">
    <img src="https://img.shields.io/github/forks/Trooped/QuickBars" alt="forks" />
  </a>
  <a href="https://github.com/Trooped/QuickBars/stargazers">
    <img src="https://img.shields.io/github/stars/Trooped/QuickBars" alt="stars" />
  </a>
  <a href="https://github.com/Trooped/QuickBars/issues/">
    <img src="https://img.shields.io/github/issues/Trooped/QuickBars" alt="open issues" />
  </a>
  <a href="https://github.com/Trooped/QuickBars/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/Trooped/QuickBars.svg" alt="license" />
  </a>
</p>
   
<h4>
    <a href="https://quickbars.app/">Official Website</a>
  <span> · </span>
    <a href="https://quickbars.app/guide">Documentation</a>
  <span> · </span>
    <a href="https://github.com/Trooped/QuickBars/issues/">Report Bug</a>
  <span> · </span>
    <a href="https://github.com/Trooped/QuickBars/issues/">Request Feature</a>
  </h4>
</div>

<br />

# :notebook_with_decorative_cover: Table of Contents

- [About the Project](#star2-about-the-project)
- [Contributing](#wave-contributing)
- [License](#warning-license)
- [Contact](#handshake-contact)
  
## :star2: About the Project

QuickBars was born out of a simple need: making smart home control accessible on the big screen. Originally developed to help my father interact with Home Assistant via larger, more readable controls, it has grown into a powerful tool downloaded by over 16,000 users worldwide.

It provides a fast, dynamic overlay that works over any app on your TV, ensuring your most-used Home Assistant actions are always just a click or two away.

### Key Capabilities:
- **Fast Overlays**: Control entities via remote-accessible sidebars.
- **Key Mapping**: Map physical remote keys (single/double/long press) to HA actions.
- **Real-time Updates**: Local connection via Home Assistant WebSocket API.
- **Camera PiPs**: Camera PiP overlay for live viewing while you're watching TV.
- **Rich Notifications**: TV-optimized alerts with images and action buttons.

For a full feature breakdown and user guides, visit the [Official Website](https://quickbars.app/).

## :wave: Contributing

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

### :running: Run Locally (For Contributors)

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

## :warning: License

Distributed under the GNU General Public License v3.0 (GPLv3). See `LICENSE` for more information.


## :handshake: Contact

Omri Peretz - [Official Website](https://quickbars.app)

Project Link: [https://github.com/Trooped/QuickBars](https://github.com/Trooped/QuickBars)
