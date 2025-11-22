# Installation

!!! warning "For Windows Users"
    If Fiji is installed in an admin-owned folder (such as `C:\Program Files\`), you need to open Fiji with **administrator privileges** every time on the next steps!

## Automatic Installation (Fiji)

1. Run **Fiji → Help → Update…**
2. Click the **Manage update sites** button
3. In the new popup window search for the site name `ZF-Utils` and activate the checkbox
4. Press the **Close** button
5. Finally, press the **Apply changes** button and restart Fiji
6. The plugin will appear under the menu Plugins → ZF Utils
7. **Installing dependencies** (this is only done once, not every update)
    - Run ZF Utils → Tools → Check Dependencies
    - Follow the instructions, you'll need to restart Fiji multiple times
### Automatic Updating
The plugin updates via the Update Manager built into Fiji. A search for updates can be triggered by running **Fiji → Help → Update…**

## Manual Installation (Legacy ImageJ)

1.  **Download the Plugin:**
    - The latest version of the plugin is a `.jar` file located in the  [`target/`](https://github.com/labmus-ufrj/tracking-plugin/tree/main/target)directory of this project.

2.  **Locate Your Fiji Installation:**
    - Find where you have Fiji installed on your computer. This is often in your `Documents` or `Program Files` folder.

3.  **Copy the `.jar` File:**
    - Copy the `.jar` file you downloaded into the `plugins` folder inside your Fiji installation directory (`Fiji.app/jars/`).

    - The final file path should look something like this:
        - `C:\Users\YourUserName\Documents\Fiji.app\plugins\ZebraFish_Utils-0.0.1.jar`
        - or `C:\Program Files\Fiji.app\plugins\ZebraFish_Utils-0.0.1.jar`

4.  **Restart Fiji:** Close and reopen Fiji to continue the installation.

5. **Installing dependencies** (this is only done once, not every update)
    - Run ZF Utils → Tools → Check Dependencies
    - Follow the instructions, you'll need to restart Fiji multiple times

### Manual Updating
Updates are manual and should be done like a re-install. Remember to replace the old `.jar` file with the new one, not leaving old versions.
