![](assets/png/jvic_readme_title.png)

![](https://github.com/lanceewing/jvic/actions/workflows/gradle.yml/badge.svg)
![](https://img.shields.io/github/v/release/lanceewing/jvic?label=Version)
![](https://img.shields.io/github/release-date/lanceewing/jvic?label=Released)


# JVIC
**JVic** is a VIC 20 emulator written in Java, using the libGDX cross-platform development framework, targeting primarily HTML5 and the web. Being such, it can run directly in your web browser:

https://vic20.games

The UI of JVic has been designed primarily with mobile devices in mind, so give it a try on your Android phone! 

## Features
- Intuitive, familiar, mobile-like UI, with game selection screens. Swipe or click to the right:
  
![](img/title_page_web_desktop.jpg)           |![](img/games_page_web_desktop.jpg) 
:-------------------------:|:-------------------------:

- Support for direct URL path access to individual games:
  - e.g. [https://vic20.games/#/pitfall](https://vic20.games/#/pitfall)
  - Or into BASIC: [https://vic20.games/#/basic](https://vic20.games/#/basic)
  - Or into BASIC with different RAM and/or TV setting: [https://vic20.games/#/basic/16k/pal](https://vic20.games/#/basic/16k/pal)
- Support for loading games via a ?url= request parameter:
  - e.g. [https://vic20.games/?url=https://raw.githubusercontent.com/raspberrypioneer/VicBoulderDash/refs/heads/main/d64/Vic20%20Boulder%20Dash.d64](https://vic20.games/?url=https://raw.githubusercontent.com/raspberrypioneer/VicBoulderDash/refs/heads/main/d64/Vic20%20Boulder%20Dash.d64)
  - e.g. [https://vic20.games/?url=https://ftp.area536.com/mirrors/scene.org/demos/groups/proxima/proxima_ppy.prg](https://vic20.games/?url=https://ftp.area536.com/mirrors/scene.org/demos/groups/proxima/proxima_ppy.prg)
  - e.g. [https://vic20.games/?url=https://www.tokra.de/vic/vicmckracken/vic%20mckracken.d64](https://vic20.games/?url=https://www.tokra.de/vic/vicmckracken/vic%20mckracken.d64)
- Support for games contained within ZIP files:
  - e.g. e.g. [https://vic20.games/?url=https://www.gamesthatwerent.com/wp-content/uploads/2021/04/Game_Moonsweeper-1983ImagicA000.zip&tv=ntsc](https://vic20.games/?url=https://www.gamesthatwerent.com/wp-content/uploads/2021/04/Game_Moonsweeper-1983ImagicA000.zip&tv=ntsc)
- Support for loading games attached to forum posts:
  - e.g. TODO: Add examples.
- Being a PWA (Progressive Web App), it can be installed locally to your device!
- And it also comes as a standalone Java app, for those who prefer Java.

## How to run games from the Home screen
JVic's game selection screens contain a list of over 100 games and demos. These games are NOT packaged with JVic but rather JVic loads the games from well known websites, such as archive.org and sleepingelephant.com.

Start by going to https://vic20.games. This will load the JVic title or home screen. There is a small question mark icon in the top right that pops up a dialog with a brief description and the current version. It mentions that in order to start playing games, simply swipe or click to the right.

![](img/jvic_android_title_screen.jpg)           |![](img/jvic_android_games_list_pg1.jpg)      |![](img/jvic_android_games_list_pg2.jpg) 
:-------------------------:|:-------------------------:|:-------------------------:

The screen works in a very similar way to the user interface of a mobile device. If you are accessing the website on a touch screen device, then you can swipe to the right to get to the next page. If you are on desktop, you can use the right arrow key, or drag/fling with your mouse, or click on the small right arrow at the bottom of the screen. Note that it has pagination indicator dots at the bottom of the screen that show how many pages there are, and which of those pages you are currently on. You can also click on those dots to jump directly to that page.

![](img/jvic_android_games_list_pg3.jpg)           |![](img/jvic_android_games_list_pg4.jpg)      |![](img/jvic_android_games_list_pg5.jpg)
:-------------------------:|:-------------------------:|:-------------------------:

Keyboard navigation within the game selection screens is also possible as follows:

* Left/Right/Up/Down: Navigates by one game in the corresponding direction.
* Home: Goes back to the JVIC title page.
* PgDn: Goes one page of games to the right.
* PgUp: Goes one page of games to the left.
* End: Goes to the last page of games.
* Enter/Space: Runs the selected game, as indicated by the white selection box.
* A-Z: Type the name of a game to scroll straight to the page containing the closest matching game.

### Open File Icon
The JVic title screen also has an Open File icon in the bottom right corner. If you click on this icon, an open file dialog will be shown. You can then select a .d64, .prg, .crt, .tap, or .zip file containing a .d64, .prg, .crt or .tap, for the emulator to run. This can be used for programs that are not available in the game selection pages, such as games that are still being developed, or that are currently for purchase through services such as itch.io and Steam. 

### Drag and Drop
As an alternative to using the Open File icon, you can instead simply drag and drop a VIC 20 program onto the JVic home screen. This will work for any VIC 20 .d64, .prg, .crt, .tap, or .zip file containing a .d64, .prg, .crt or .tap. The dropped program will be immediately run by the emulator.
  
## The Machine screen
When a game is run, the machine screen is displayed. It shows the VIC 20 screen and various icons, which may be either at the bottom of the screen (for portrait) or to the sides (for landscape).

The following two screen shots show the icons when running in Chrome on a Windows machine:

![](img/jvic_game_1.jpg)           |![](img/jvic_game_2.jpg) 
:-------------------------:|:-------------------------:

And the three below show the placement of the icons when running in Portrait mode as an installed PWA (Progressive Web App) on an Android phone:

![](img/jvic_android_game_1.jpg)           |![](img/jvic_android_game_2.jpg)      |![](img/jvic_android_game_3.jpg) 
:-------------------------:|:-------------------------:|:-------------------------:

The function of these icons is as follows:

Icons                                 | Description                          |Icons                                 | Description                          
:------------------------------------:|:------------------------------------:|:------------------------------------:|:------------------------------------:
![](assets/png/full_screen.png)       |Toggles full screen mode.             |![](assets/png/keyboard_icon.png)     |Toggles display of the keyboard.
![](assets/png/unmute_icon.png)       |Turns on sound.                       |![](assets/png/mute_icon.png)         |Turns off sound.
![](assets/png/pause.png)             |Pauses the emulator.                  |![](assets/png/play.png)              |Resumes emulation.
![](assets/png/joystick_icon.png)     |Toggles display of joystick.          |![](assets/png/back_arrow.png)        |Goes back to the JVic home screen.
![](assets/png/screen_icon.png)       |Changes screen size.                  |                                      |

It is important to note that if you have accessed JVic directly via a game URL (either one of the /#/ paths, or by using the ?url= request parameter), then the speaker icon will initially be muted. This is because web browsers do not allow sound to be played automatically without a user interaction. In this scenario, you will need to click the speaker icon to turn sound on.

If instead you have started a game by clicking on the game's thumbnail image from JVic's game list screen, then sound will be unmuted automatically.

## Installing the web app on your device.
JVic follows the PWA (Progressive Web App) spec and can therefore be installed on your device. When you go to the https://vic20.games web site, and you haven't previously installed JVic, then the browser may promote it to you for installation. In Chrome, one of the ways it does this is to show a little installation icon at the end of the browser location field, as indicated by the yellow arrow in the screenshot below. Clicking on that icon will install JVic as if it were a standalone app on your device.

![](img/jvic_install_icon.jpg)           |![](img/jvic_install_app_popup.jpg) 
:-------------------------:|:-------------------------:

On Windows, this means that you'll see JVic in your Windows menu, and it can be pinned to your task bar, if you choose to do that. On a mobile device, such as an Android phone, it will install it alongside all the other apps on your phone and can then be launched like any other standalone app on your phone. Internally, it still runs the web version though from the website, so is automatically kept up to date with new releases.

## Installing the Java verson
JVic is a cross platform application. The release build creates both the web version, which is deployed to https://vic20.games, and also a Java version that is available under the Releases page of the github project:

https://github.com/lanceewing/jvic/releases/latest

The .jar file is an executable jar. If you have the Java virtual machine installed on your computer, then running the downloaded JVic jar is as easy as double clicking the .jar file.

Java runs on many platforms, including Windows, Mac, and Linux, and so this is another way to run JVic on your device. On Windows, for example, you could choose to install the web version (as described earlier), or you could download and run the Java version. If JVic does not start up when you double click the jar, then it most likely means that you do not have Java installed.

Not everyone is a fan of Java desktop apps, due to the overhead of downloading and installing the Java virtual machine, so if you do not already have Java installed, then I would recommend using the web version, as the web version should work on any modern web browser.

## Installing the Android APK
JVic's build process also creates a native Android APK file. This can be found as a separate asset available under the Releases page:

https://github.com/lanceewing/jvic/releases/latest

The .apk file has been signed, but there are no plans to make this available in the Google Play Store. It is provided for those who might like a native Android build as an alternative to installing the web PWA. Simply download the .apk file from the Releases page and your phone should prompt you asking if you'd like to install it. Not being from the Google Play Store though, you'll get some warnings about it coming from an unknown source.

Personally, I prefer to install the web version to my Android phone, as a PWA app (see section above), as its more convenient and updates automatically. You may find that the sound performs better in the native APK version though.

## Running on your own local web server
As the web version of JVic is a essentially static content, you could, if you like, download the release ZIP, extract it, and run it by serving it from your own web server, rather than the vic20.games web site. There are a couple of gotchas with this though: JVic uses some browser APIs that are not enabled by default. For example, it uses SharedArrayBuffer for multiple things (keyboard events, graphics, sound), as it is a very quick way to share data between the web worker that runs the emulation, the audio thread, and the main browser UI thread. In order to enable this API, the web server must set the following two HTTP response headers in every response:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

(For more details, see here: https://developer.mozilla.org/en-US/docs/Web/API/Window/crossOriginIsolated)

To make this as easy as possible for you, the JVic ZIP file for the web version comes with some .swshtaccess files in three of the folders that are designed to be read by the "Simple Web Server" web server. They are like .htaccess files that you may already be familiar with but for the Simple Web Server instead (SWS). 

1. Download the release ZIP of jvic, e.g.: https://github.com/lanceewing/jvic/releases/download/v1.0.8/jvic-web-v1.0.8.zip
2. Extract the jvic ZIP into a folder.
3. Download Simple Web Server for your platform from here: https://simplewebserver.org/download.html
4. Start up Simple Web Server
5. Click "New Server" button in bottom right corner
6. For the folder path, choose the folder that contains the index.html file from the extracted jvic ZIP
7. Choose your port number. It works with port 80, so that is what I use, but could be anything, e.g. 8080, 8081, etc.
8. Expand the "Advanced Rules" section and tick the "Enable .swshtaccess configuration files" checkbox.
9. Click the "Create Server" button in the bottom right. This will start up the web server..
10. Now go to the web browser and access localhost on the port you chose. It should load the JVic home screen. Scroll to the right to choose a game.

It is the .swshtaccess files within the jvic ZIP that automatically take case of the rest of the Simple Web Server configuration, such as adding those two HTTP response headers mentioned earlier. These are the relative paths if you're interested in seeing what they do:

```
./.swshtaccess
./html/.swshtaccess
./worker/.swshtaccess
```

They mostly do the same thing, which is to add the "Cross-Origin-Opener-Policy" and "Cross-Origin-Embedder-Policy" HTTP response headers mentioned above. The top level .swshtaccess file also sets up the localhost equivalent of the CORS proxy, which is used to load VIC 20 games from other websites, such as sleepingelephant.com.

The Simple Web Server is an Electron app, so not really designed to be used as a full web server. The theory can be applied to any web server though. All you need to do is configure those same features. For example, the vic20.games web site is hosted by Cloudflare Pages, and so it uses a Cloudflare _headers config file to apply those HTTP response headers. This file is also included in the repo, if you are interested in seeing what it does.

## Credits and Acknowledgements
This project would not have been possible without the following projects and their authors:

- [libgdx](https://libgdx.com/): The cross-platform game development framework.
- [gdx-liftoff](https://github.com/libgdx/gdx-liftoff): Written by Tommy Ettinger. Used to generate the initial libgdx project boilerplate.
- [gwt-webworker](https://gitlab.com/ManfredTremmel/gwt-webworker): Written by Manfred Trammel. The key to running libgdx gwt code in a web worker.
- [gwt-jszip](https://github.com/ainslec/GWTJSZip): Originally written by Aki Miyazaki, extended by Chris Ainsley.
- [jszip](https://github.com/Stuk/jszip): Written by Stuart Knightley. Used by JVIC to unzip imported games.
- [GWT](https://www.gwtproject.org): Google Web Toolkit, used by libgdx to transpile the JVIC Java code to JavaScript.
- [ringbuf.js](https://github.com/padenot/ringbuf.js/blob/main/js/ringbuf.js): Written by Paul Adenot. Used for the keyboard matrix, audio queue and pixel array in JVIC.
- [dialog.js](https://css-tricks.com/replace-javascript-dialogs-html-dialog-element/): Written by Mads Stoumann. Used for most of the dialogs.

