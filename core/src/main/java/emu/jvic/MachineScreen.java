package emu.jvic;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import emu.jvic.config.AppConfigItem;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.MachineInputProcessor;
import emu.jvic.ui.ViewportManager;
import emu.jvic.ui.MachineInputProcessor.JoystickAlignment;
import emu.jvic.ui.MachineInputProcessor.ScreenSize;

/**
 * The main screen in the JVic emulator, i.e. the one that shows the video output of the VIC.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

    private enum ScreenFilterMode {
        NEAREST,
        SOFT,
        LINEAR
    }

    private static final float SOFT_FILTER_BLEND = 0.4f;

    private static final String SOFT_FILTER_VERTEX_SHADER = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";

    private static final String SOFT_FILTER_FRAGMENT_SHADER = "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform sampler2D u_textureLinear;\n"
            + "uniform vec2 u_textureSize;\n"
            + "uniform float u_softness;\n"
            + "\n"
            + "void main() {\n"
            + "    vec2 texelSize = vec2(1.0) / u_textureSize;\n"
            + "    vec2 nearestCoords = (floor(v_texCoords * u_textureSize) + 0.5) * texelSize;\n"
            + "    nearestCoords = clamp(nearestCoords, texelSize * 0.5, vec2(1.0) - (texelSize * 0.5));\n"
            + "    vec4 nearestColor = texture2D(u_texture, nearestCoords);\n"
            + "    vec4 linearColor = texture2D(u_textureLinear, v_texCoords);\n"
            + "    gl_FragColor = v_color * mix(nearestColor, linearColor, u_softness);\n"
            + "}\n";

    /**
     * The Game object for JVic. Allows us to easily change screens.
     */
    private JVic jvic;

    /**
     * Platform specific JVicRunner implementation.
     */
    private JVicRunner jvicRunner;

    /**
     * The InputProcessor for the MachineScreen. Handles the key and touch input.
     */
    private MachineInputProcessor machineInputProcessor;

    /**
     * This is an InputMultiplexor, which includes both the Stage and the
     * MachineScreen.
     */
    private InputMultiplexer portraitInputProcessor;
    private InputMultiplexer landscapeInputProcessor;
    
    /**
     * SpriteBatch shared by all rendered components.
     */
    private SpriteBatch batch;

    // Currently in use components to support rendering of the VIC 20 screen. The
    // objects that these references point to will change depending on the MachineType.
    private Pixmap screenPixmap;
    private ExtendViewport viewport;
    private Camera camera;
    private Texture[] screens;

    private int drawScreen = 1;
    private int updateScreen = 0;
    
    private ScreenFilterMode screenFilterMode = ScreenFilterMode.NEAREST;
    private float softFilterBlend = SOFT_FILTER_BLEND;
    private ShaderProgram softFilterShader;

    // Screen resources for each MachineType.
    private Map<MachineType, Pixmap> machineTypePixmaps;
    private Map<MachineType, Camera> machineTypeCameras;
    private Map<MachineType, ExtendViewport> machineTypeViewports;
    private Map<MachineType, Texture[]> machineTypeTextures;

    // UI components.
    private Texture screenSizeIcon;
    private Texture playIcon;
    private Texture pauseIcon;
    private Texture muteIcon;
    private Texture unmuteIcon;
    private Texture joystickIcon;
    private Texture keyboardIcon;
    private Texture backIcon;
    private Texture fullScreenIcon;
    private Texture warpSpeedIcon;
    private Texture cameraIcon;
    
    private ViewportManager viewportManager;

    // Touchpad
    private Stage portraitStage;
    private Stage landscapeStage;
    private Touchpad portraitTouchpad;
    private Touchpad landscapeTouchpad;
    private Image portraitFireButton;
    private Image landscapeFireButton;
    private int previousDirection;

    // FPS text font
    private BitmapFont font;

    private boolean showFPS;

    /**
     * Details about the application currently running.
     */
    private AppConfigItem appConfigItem;

    /**
     * Whether or not the game was started by a user interaction.
     */
    private boolean startedByUser;

    /**
     * PAL or NTSC. 
     */
    private MachineType machineType;

    /**
     * The screen size setting during the last draw.
     */
    private ScreenSize lastScreenSize;
    
    /**
     * Constructor for MachineScreen.
     * 
     * @param jvic 
     * @param jvicRunner 
     * @param dialogHandler 
     */
    public MachineScreen(JVic jvic, JVicRunner jvicRunner, DialogHandler dialogHandler) {
        this.jvic = jvic;
        this.jvicRunner = jvicRunner;

        // We default to PAL prior to a program being selected. It doesn't really matter
        // what the default is.
        this.machineType = MachineType.PAL;
        
        jvicRunner.init(this, machineType.getTotalScreenWidth(), machineType.getTotalScreenHeight());
        
        batch = new SpriteBatch();
        softFilterShader = createSoftFilterShader();

        machineTypePixmaps = new HashMap<MachineType, Pixmap>();
        machineTypeTextures = new HashMap<MachineType, Texture[]>();
        machineTypeViewports = new HashMap<MachineType, ExtendViewport>();
        machineTypeCameras = new HashMap<MachineType, Camera>();

        createScreenResourcesForMachineType(MachineType.PAL);
        createScreenResourcesForMachineType(MachineType.NTSC);
        createScreenResourcesForMachineType(MachineType.VIC44);
        createScreenResourcesForMachineType(MachineType.VIC44K);

        // TeaVM can deliver resize callbacks before a program selection has bound the
        // active machine resources, so keep a safe default viewport/camera/screen live.
        activateScreenResources(MachineType.PAL);

        warpSpeedIcon = new Texture("png/warp_speed_icon.png");
        cameraIcon = new Texture("png/camera_icon.png");
        screenSizeIcon = new Texture("png/screen_icon.png");
        playIcon = new Texture("png/play.png");
        pauseIcon = new Texture("png/pause.png");
        muteIcon = new Texture("png/mute_icon.png");
        unmuteIcon = new Texture("png/unmute_icon.png");
        keyboardIcon = new Texture("png/keyboard_icon.png");
        joystickIcon = new Texture("png/joystick_icon.png");
        backIcon = new Texture("png/back_arrow.png");
        fullScreenIcon = new Texture("png/full_screen.png");

        // Create the portrait and landscape joystick touchpads.
        portraitTouchpad = createTouchpad(300);
        portraitFireButton = createFireButton(300);
        landscapeTouchpad = createTouchpad(200);
        landscapeFireButton = createFireButton(200);
        
        viewportManager = ViewportManager.getInstance();

        // Create a Stage and add TouchPad
        portraitStage = new Stage(viewportManager.getPortraitViewport(), batch);
        portraitStage.addActor(portraitTouchpad);
        portraitStage.addActor(portraitFireButton);
        landscapeStage = new Stage(viewportManager.getLandscapeViewport(), batch);
        landscapeStage.addActor(landscapeTouchpad);
        landscapeStage.addActor(landscapeFireButton);

        // Create and register an input processor for keys, etc.
        machineInputProcessor = new MachineInputProcessor(this, dialogHandler);
        portraitInputProcessor = new InputMultiplexer();
        portraitInputProcessor.addProcessor(jvicRunner.getKeyboardMatrix());
        portraitInputProcessor.addProcessor(portraitStage);
        portraitInputProcessor.addProcessor(machineInputProcessor);
        landscapeInputProcessor = new InputMultiplexer();
        landscapeInputProcessor.addProcessor(jvicRunner.getKeyboardMatrix());
        landscapeInputProcessor.addProcessor(landscapeStage);
        landscapeInputProcessor.addProcessor(machineInputProcessor);

        // If the application is running on mobile web, then show FPS enabled, but
        // for desktop web, turn off by default.
        //showFPS = jvicRunner.isMobile();

        // FPS font
        font = new BitmapFont(Gdx.files.internal("data/default.fnt"), false);
        font.setFixedWidthGlyphs(".  *"); // Note: The * and . are ignored, first and last. Only the space is fixed width.
        font.setColor(new Color(0x808080FF));
        font.getData().setScale(2f, 2f);
    }
    
    protected Touchpad createTouchpad(int size) {
        Skin touchpadSkin = new Skin();
        touchpadSkin.add("touchBackground", new Texture("png/joystick_background.png"));
        touchpadSkin.add("touchKnob", new Texture("png/joystick_knob.png"));
        TouchpadStyle touchpadStyle = new TouchpadStyle();
        Drawable touchBackground = touchpadSkin.getDrawable("touchBackground");
        Drawable touchKnob = touchpadSkin.getDrawable("touchKnob");
        touchpadStyle.background = touchBackground;
        touchpadStyle.knob = touchKnob;
        Touchpad touchpad = new Touchpad(10, touchpadStyle);
        touchpad.setBounds(15, 15, size, size);
        touchpad.setVisible(false);
        return touchpad;
    }
    
    private Image createFireButton(int size) {
        Image image = new Image(new Texture("png/joystick_button.png"));
        image.setBounds(15, 15, size, size);
        image.addListener(fireButtonClickListener);
        image.setVisible(false);
        return image;
    }

    /**
     * Initialises the Machine with the given AppConfigItem. This will represent an
     * app that was selected on the HomeScreen. As part of this initialisation, it
     * creates the Pixmap, screen Textures, Camera and Viewport required to render
     * the VIC 20 screen at the size needed for the MachineType being emulated.
     * 
     * @param appConfigItem The configuration for the app that was selected on the HomeScreen.
     * @param startedByUser Whether the program is being started via a user interaction, or not.
     */
    public void initMachine(AppConfigItem appConfigItem, boolean startedByUser) {
        this.appConfigItem = appConfigItem;
        this.startedByUser = startedByUser;

        // NOTE: The Machine is recreated and initialised by the start method in
        // JVicRunner.

        // Switch libGDX screen resources used by the VIC screen to the size required
        // by the MachineType.
        MachineType selectedMachineType = MachineType.valueOf(appConfigItem.getMachineType());
        activateScreenResources(selectedMachineType);

        // Reinitialise the platform pixel buffer to the active machine dimensions.
        jvicRunner.init(this, selectedMachineType.getTotalScreenWidth(), selectedMachineType.getTotalScreenHeight());

        drawScreen = 1;
        updateScreen = 0;

        setTextureFilterMode(appConfigItem.getTextureFilter());
        clearActiveScreenBuffers();
    }

    private void activateScreenResources(MachineType machineType) {
        this.machineType = machineType;
        ensureScreenResources(machineType);
    }

    private void ensureActiveScreenResources() {
        MachineType activeMachineType = (machineType != null ? machineType : MachineType.PAL);
        if ((screenPixmap == null) || (screens == null) || (camera == null) || (viewport == null)) {
            activateScreenResources(activeMachineType);
        }
        ensureScreenResources(activeMachineType);
    }

    private void ensureScreenResources(MachineType machineType) {
        Pixmap activeScreenPixmap = machineTypePixmaps.get(machineType);
        Texture[] activeScreens = machineTypeTextures.get(machineType);
        Camera activeCamera = machineTypeCameras.get(machineType);
        ExtendViewport activeViewport = machineTypeViewports.get(machineType);

        if ((activeScreenPixmap == null) || (activeScreens == null) || (activeCamera == null) || (activeViewport == null)) {
            createScreenResourcesForMachineType(machineType);
            activeScreenPixmap = machineTypePixmaps.get(machineType);
            activeScreens = machineTypeTextures.get(machineType);
            activeCamera = machineTypeCameras.get(machineType);
            activeViewport = machineTypeViewports.get(machineType);
        }

        this.screenPixmap = activeScreenPixmap;
        this.screens = activeScreens;
        this.camera = activeCamera;
        this.viewport = activeViewport;
    }

    /**
     * Creates the libGDX screen resources required for the given MachineType.
     * 
     * @param machineType The MachineType to create the screen resources for.
     */
    private void createScreenResourcesForMachineType(MachineType machineType) {
        // Create the libGDX screen resources used by the VIC 20 screen to the size
        // required by the MachineType.
        Pixmap screenPixmap = new Pixmap(machineType.getTotalScreenWidth(), machineType.getTotalScreenHeight(),
                Pixmap.Format.RGBA8888);
        Texture[] screens = new Texture[6];
        screens[0] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[0].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[1] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[1].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[2] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[2].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[3] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[3].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[4] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[4].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[5] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[5].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        Camera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(
                (machineType.getVisibleScreenHeight() / 3) * 4,
                machineType.getVisibleScreenHeight(), 
                camera);

        machineTypePixmaps.put(machineType, screenPixmap);
        machineTypeTextures.put(machineType, screens);
        machineTypeCameras.put(machineType, camera);
        machineTypeViewports.put(machineType, viewport);
    }

    private long renderCount;

    @Override
    public void render(float delta) {
        long fps = Gdx.graphics.getFramesPerSecond();
        boolean draw = false;

        if (jvicRunner.hasStopped()) {
            // If game has ended then go back to home screen. It has to be the UI thread
            // that calls the setScreen method. The JVicRunner itself can't do this.
            jvicRunner.reset();
            // This makes sure we update the Pixmap one last time before leaving, as that
            // will mean that the next game screen starts out black for the next game.
            copyPixels();
            if (Gdx.graphics.isFullscreen()) {
                machineInputProcessor.switchOutOfFullScreen();
            }
            jvic.setScreen(jvic.getHomeScreen());
            return;
        }
        
        if (jvicRunner.isPaused()) {
            // When paused, we limit the draw frequency since there isn't anything to change.
            draw = ((fps < 30) || ((renderCount % (fps / 30)) == 0));

        } else {
            if (jvicRunner.hasNewFrame()) {
                copyPixels();
            }
            draw = true;
        }

        if (draw) {
            draw(delta);
        }
        
        // Process any delayed key releases that are pending.
        jvicRunner.getKeyboardMatrix().checkDelayedReleaseKeys();
        
        renderCount++;
    }
    
    public boolean copyPixels() {
        ensureActiveScreenResources();
        jvicRunner.updatePixmap(screenPixmap);
        ScreenFilterMode activeFilterMode = getActiveScreenFilterMode();
        screens[updateScreen + getTextureOffset(activeFilterMode)].draw(screenPixmap, 0, 0);
        if (activeFilterMode == ScreenFilterMode.SOFT) {
            // The SOFT filter mode is a blend between the NEAREST and LINEAR filter 
            // modes, so we need to update both the NEAREST and LINEAR textures with 
            // the new screen pixels. The percentage value then determines the blend.
            screens[updateScreen + getTextureOffset(ScreenFilterMode.LINEAR)].draw(screenPixmap, 0, 0);
        }
        updateScreen = (updateScreen + 1) % 3;
        drawScreen = (drawScreen + 1) % 3;
        return true;
    }

    private void clearActiveScreenBuffers() {
        ensureActiveScreenResources();
        screenPixmap.setColor(0, 0, 0, 1);
        screenPixmap.fill();
        for (Texture screen : screens) {
            screen.draw(screenPixmap, 0, 0);
        }
    }
    
    private void draw(float delta) {
        // Get the KeyboardType currently being used by the MachineScreenProcessor.
        KeyboardType keyboardType = machineInputProcessor.getKeyboardType();
        JoystickAlignment joystickAlignment = machineInputProcessor.getJoystickAlignment();
        ScreenSize currentScreenSize = machineInputProcessor.getScreenSize();
        int renderWidth = currentScreenSize.getRenderWidth(machineType);
        int renderHeight = currentScreenSize.getRenderHeight(machineType);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        
        final int ADJUSTED_WIDTH = ((machineType.getVisibleScreenHeight() / 3) * 4);
        final int ADJUSTED_HEIGHT = machineType.getVisibleScreenHeight();

        // Render the VIC screen.
        float cameraXOffset = 0;
        float cameraYOffset = 0;
        float sidePaddingWidth = viewportManager.getSidePaddingWidth();
        
        if (viewportManager.doesScreenFitWidth()) {
            // Override default screen centering logic to allow for narrower screens, so 
            // that the joystick can be rendered as a decent size.
            float vicWidthRatio = (viewportManager.getVICScreenWidth() / ADJUSTED_WIDTH);
            if ((sidePaddingWidth > 64) && (sidePaddingWidth < 128)) {
                // Icons on one side.
                // 128 = 2 * min width on sides.
                // 64 = when icon on one side is perfectly centred.
                float unadjustedXOffset = Math.min(128 - sidePaddingWidth, sidePaddingWidth);
                cameraXOffset = (unadjustedXOffset / vicWidthRatio);
            }
            if (!currentScreenSize.equals(ScreenSize.FIT)) {
                // Adjusts the position of screen to align with the top.
                int screenHeight = viewportManager.getCurrentViewport().getScreenHeight();
                cameraYOffset = (((screenHeight / 2) - renderHeight)) + (renderHeight / 2);
            }
        } else {
            float vicScreenHeight = (viewportManager.getWidth() / 1.33f);
            float vicHeightRatio = (vicScreenHeight / ADJUSTED_HEIGHT);
            float topPadding = ((viewportManager.getHeight() - vicScreenHeight) / 2);
            cameraYOffset = (topPadding / vicHeightRatio);
        }
        machineInputProcessor.setCameraXOffset(cameraXOffset);
        if (currentScreenSize != lastScreenSize) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        lastScreenSize = currentScreenSize;
        float targetCameraX = (renderWidth / 2f) + cameraXOffset;
        float targetCameraY = (renderHeight / 2f) - cameraYOffset;

        if (!currentScreenSize.equals(ScreenSize.FIT)) {
            // For non-FIT modes, snap camera to the viewport parity lattice (n or n+0.5)
            // so nearest-neighbour scaling lands on consistent pixel rows/columns.
            targetCameraX = snapCameraToViewportParity(targetCameraX, viewport.getWorldWidth());
            targetCameraY = snapCameraToViewportParity(targetCameraY, viewport.getWorldHeight());
        }

        camera.position.set(targetCameraX, targetCameraY, 0.0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        ScreenFilterMode activeFilterMode = getActiveScreenFilterMode();
        if (activeFilterMode == ScreenFilterMode.SOFT) {
            batch.setShader(softFilterShader);
        }
        batch.begin();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, 1f);
        if (activeFilterMode == ScreenFilterMode.SOFT) {
            Texture linearScreen = screens[drawScreen + getTextureOffset(ScreenFilterMode.LINEAR)];
            linearScreen.bind(1);
            softFilterShader.setUniformi("u_textureLinear", 1);
            softFilterShader.setUniformf("u_textureSize", (float) linearScreen.getWidth(), (float) linearScreen.getHeight());
            softFilterShader.setUniformf("u_softness", softFilterBlend);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        }
        
        // Texture isn't always drawn to match physical pixels.
        batch.draw(
                screens[drawScreen + getTextureOffset(activeFilterMode)], 
                0, 0, renderWidth, renderHeight,
                machineType.getHorizontalOffset(), machineType.getVerticalOffset(), 
                machineType.getVisibleScreenWidth(), machineType.getVisibleScreenHeight(), 
                false, false);
        batch.end();
        if (activeFilterMode == ScreenFilterMode.SOFT) {
            batch.setShader(null);
        }

        // Render the UI elements, e.g. the keyboard and joystick icons.
        viewportManager.getCurrentCamera().update();
        batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
        batch.enableBlending();
        batch.begin();
        
        // The keyboard is always render in portrait mode, as there is space for it,
        // but in landscape mode, it needs to be enabled via the keyboard icon.
        if (keyboardType.isRendered() || viewportManager.isPortrait()) {
            if (keyboardType.getTexture() != null) {
                batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
                batch.draw(
                        keyboardType.getTexture(), 
                        0, keyboardType.getRenderOffset(), 
                        keyboardType.getTexture().getWidth(), 
                        keyboardType.getHeight());
            }
        }
        
        batch.setColor(c.r, c.g, c.b, 0.5f);
        
        // Some icons change depending on state.
        Texture speakerIcon = machineInputProcessor.isSpeakerOn()? muteIcon : unmuteIcon;
        Texture pausePlayIcon = jvicRunner.isPaused()? playIcon : pauseIcon;
        
        if (viewportManager.isPortrait()) {
            // Portrait
            batch.draw(fullScreenIcon, 20, 20);
            batch.draw(screenSizeIcon, (viewportManager.getWidth() / 6) - 16, 20);
            batch.draw(speakerIcon, (viewportManager.getWidth() / 3) - 32, 20);
            batch.draw(pausePlayIcon, (viewportManager.getWidth() / 2) - 48, 20);
            batch.draw(keyboardIcon, (viewportManager.getWidth() - (viewportManager.getWidth() / 3)) - 64, 20);
            batch.draw(joystickIcon, (viewportManager.getWidth() - (viewportManager.getWidth() / 6)) - 80, 20);
            batch.draw(backIcon, viewportManager.getWidth() - 116, 20);
        } else {
            // Landscape
            if (cameraXOffset == 0) {
                // Middle.
                if ((viewportManager.getVICScreenBase() > 0) || (sidePaddingWidth <= 64)) {
                    // The area between full landscape and full portrait.
                    float leftAdjustment = (viewportManager.getWidth() / 4) - 48;
                    batch.draw(fullScreenIcon, ((viewportManager.getWidth() - ((viewportManager.getWidth() * 6 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(screenSizeIcon, ((viewportManager.getWidth() - ((viewportManager.getWidth() * 5 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(speakerIcon,    ((viewportManager.getWidth() - ((viewportManager.getWidth() * 4 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(pausePlayIcon,  ((viewportManager.getWidth() - ((viewportManager.getWidth() * 3 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(keyboardIcon,   ((viewportManager.getWidth() - ((viewportManager.getWidth() * 2 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(joystickIcon,   ((viewportManager.getWidth() - ((viewportManager.getWidth() * 1 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(backIcon,       ((viewportManager.getWidth() - ((viewportManager.getWidth() * 0 ) / 12)) - 96) - leftAdjustment, 16);
                } else {
                    // Normal landscape. Icons both sides.
                    batch.draw(speakerIcon,   16, viewportManager.getHeight() - 112);
                    batch.draw(pausePlayIcon, 16, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    if (joystickAlignment.equals(JoystickAlignment.RIGHT)) { 
                        batch.draw(joystickIcon,  16, (viewportManager.getHeight() / 3) - 32);
                    }
                    batch.draw(keyboardIcon,  16, 0);
                    batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, viewportManager.getHeight() - 112);
                    batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    if (!joystickAlignment.equals(JoystickAlignment.RIGHT)) {
                        batch.draw(joystickIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                    }
                    batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
                }
            } else if (cameraXOffset > 0) {
                // Right side.
                batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - 112));
                batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 6)) - 80);
                batch.draw(speakerIcon,    viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                batch.draw(pausePlayIcon,  viewportManager.getWidth() - 112, (viewportManager.getHeight() / 2) - 48);
                batch.draw(joystickIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                batch.draw(keyboardIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 6) - 16);
                batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
            }
        }

        if (showFPS) {
            StringBuilder overlayText = new StringBuilder();
            
            String performanceStats = jvicRunner.getPerformanceStatsText();
            if ((performanceStats != null) && !performanceStats.isEmpty()) {
                overlayText.append('\n');
                overlayText.append(performanceStats);
            }

            float overlayX = ((!viewportManager.isPortrait()) && (cameraXOffset == 0) ? 140 : 20);
            font.draw(batch, overlayText, overlayX, viewportManager.getHeight() - 20);
        }
        
        batch.end();
        
        // The joystick touch pad is updated and rendered via the Stage.
        if (!joystickAlignment.equals(JoystickAlignment.OFF)) {
            float joyX = 0;
            float joyY = 0;
            if (viewportManager.isPortrait()) {
                // Top of keyboard is: 756 + 135 = 891.
                int joyWidth = 200;
                int topOfButtons = KeyboardType.PORTRAIT.getRenderOffset();
                int topOfKeyboard = topOfButtons + (int)KeyboardType.PORTRAIT.getHeight();
                int vicScreenBase = viewportManager.getVICScreenBase();
                int gapForJoystick = 0;
                int joystickBase = 0;
                int midBetweenKeybAndPic = ((vicScreenBase + 891) / 2);
                if (keyboardType.equals(KeyboardType.OFF)) {
                    gapForJoystick = vicScreenBase - topOfButtons;
                    joystickBase = ((vicScreenBase + topOfButtons) / 2) - (joyWidth / 2);
                } else {
                    gapForJoystick = vicScreenBase - topOfKeyboard;
                    if (gapForJoystick >= joyWidth) {
                        joystickBase = midBetweenKeybAndPic - (joyWidth / 2);
                    } else {
                        joystickBase = topOfKeyboard + 16;
                    }
                }
                portraitTouchpad.setVisible(true);
                portraitTouchpad.setSize(joyWidth, joyWidth);
                portraitTouchpad.setY(joystickBase);
                if (gapForJoystick >= joyWidth) {
                    portraitFireButton.setVisible(true);
                    portraitFireButton.setSize(joyWidth, joyWidth);
                    portraitFireButton.setY(joystickBase);
                } else {
                    portraitFireButton.setVisible(false);
                }
                switch (joystickAlignment) {
                    case OFF:
                        break;
                    case RIGHT:
                        portraitTouchpad.setX(1080 - joyWidth - 20);
                        portraitFireButton.setX(20);
                        break;
                    case LEFT:
                        portraitTouchpad.setX(20);
                        portraitFireButton.setX(1080 - joyWidth - 20);
                        break;
                }
                portraitStage.act(delta);
                portraitStage.draw();
                joyX = portraitTouchpad.getKnobPercentX();
                joyY = portraitTouchpad.getKnobPercentY();
            } else {
                // Landscape
                if ((viewportManager.getVICScreenBase() > 0) || (sidePaddingWidth <= 128)) {
                    // Icons at bottom. Joystick at bottom.
                    int joyWidth = 200;
                    landscapeTouchpad.setVisible(true);
                    landscapeTouchpad.setSize(joyWidth, joyWidth);
                    landscapeTouchpad.getStyle().knob.setMinHeight(joyWidth * 0.6f);
                    landscapeTouchpad.getStyle().knob.setMinWidth(joyWidth * 0.6f);
                    landscapeTouchpad.setY(0);
                    landscapeFireButton.setVisible(false);
                    switch (joystickAlignment) {
                        case OFF:
                            break;
                        case RIGHT:
                            if (sidePaddingWidth < 64) {
                                landscapeTouchpad.setX(1920 - joyWidth);
                            } else {
                                machineInputProcessor.setJoystickAlignment(JoystickAlignment.LEFT);
                            }
                            break;
                        case LEFT:
                            if (sidePaddingWidth < 64) {
                                landscapeTouchpad.setX(0);
                            } else {
                                landscapeTouchpad.setX(0);
                            }
                            break;
                    }
                    landscapeStage.act(delta);
                    landscapeStage.draw();
                    joyX = landscapeTouchpad.getKnobPercentX();
                    joyY = landscapeTouchpad.getKnobPercentY();
                } else {
                    // Joystick left and right.
                    float joyWidth = Math.min(sidePaddingWidth, 200) - 10;
                    landscapeTouchpad.setVisible(true);
                    landscapeTouchpad.setSize(joyWidth, joyWidth);
                    landscapeTouchpad.getStyle().knob.setMinHeight(joyWidth * 0.6f);
                    landscapeTouchpad.getStyle().knob.setMinWidth(joyWidth * 0.6f);
                    landscapeTouchpad.setY((viewportManager.getHeight() / 3) - (joyWidth / 2));
                    landscapeFireButton.setVisible(false);
                    switch (joystickAlignment) {
                        case OFF:
                            break;
                        case RIGHT:
                            landscapeTouchpad.setX(1920 - joyWidth - 0);
                            break;
                        case LEFT:
                            landscapeTouchpad.setX(4);
                            break;
                    }
                    landscapeStage.act(delta);
                    landscapeStage.draw();
                    joyX = landscapeTouchpad.getKnobPercentX();
                    joyY = landscapeTouchpad.getKnobPercentY();
                }
            }
            processJoystickInput(joyX, joyY);
        } else {
            // Hide joystick. Ensures it will not receive touch events.
            if (viewportManager.isPortrait()) {
                portraitTouchpad.setVisible(false);
                landscapeTouchpad.setVisible(false);
            } else {
                landscapeTouchpad.setVisible(false);
                landscapeFireButton.setVisible(false);
            }
        }
    }

    private float snapCameraToViewportParity(float cameraValue, float viewportWorldSize) {
        float desiredFraction = Math.abs((viewportWorldSize * 0.5f) % 1f);
        return Math.round(cameraValue - desiredFraction) + desiredFraction;
    }
    
    public ClickListener fireButtonClickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
            KeyboardMatrix keyboardMatrix = jvicRunner.getKeyboardMatrix();
            keyboardMatrix.vicKeyDown(VicKeys.JOYSTICK_FIRE);
            keyboardMatrix.vicKeyUp(VicKeys.JOYSTICK_FIRE);
        }
    };
    
    private static final int[] DIRECTION_TO_KEY_MAP = new int[] {
            0, 
            Keys.NUMPAD_8,  // Up
            Keys.NUMPAD_9,  // NE
            Keys.NUMPAD_6,  // Right
            Keys.NUMPAD_3,  // SE
            Keys.NUMPAD_2,  // Down
            Keys.NUMPAD_1,  // SW
            Keys.NUMPAD_4,  // Left
            Keys.NUMPAD_7   // NW
    };
        
    /**
     * Processes joystick input, converting the touchpad position into an AGI
     * direction and then setting the corresponding direction key.
     * 
     * @param joyX
     * @param joyY
     */
    private void processJoystickInput(float joyX, float joyY) {
        double heading = Math.atan2(-joyY, joyX);
        double distance = Math.sqrt((joyX * joyX) + (joyY * joyY));
        
        int direction = 0;
        
        if (distance > 0.3) {
            // Convert heading to an VIC direction.
            if (heading == 0) {
                // Right
                direction = 3;
            }
            else if (heading > 0) {
                // Down
                if (heading < 0.3926991) {
                    // Right
                    direction = 3;
                }
                else if (heading < 1.178097) {
                    // Down/Right
                    direction = 4;
                }
                else if (heading < 1.9634954) {
                    // Down
                    direction = 5;
                }
                else if (heading < 2.7488936) {
                    // Down/Left
                    direction = 6;
                }
                else {
                    // Left
                    direction = 7;
                }
            }
            else {
                // Up
                if (heading > -0.3926991) {
                    // Right
                    direction = 3;
                }
                else if (heading > -1.178097) {
                    // Up/Right
                    direction = 2;
                }
                else if (heading > -1.9634954) {
                    // Up
                    direction = 1;
                }
                else if (heading > -2.7488936) {
                    // Up/Left
                    direction = 8;
                }
                else {
                    // Left
                    direction = 7;
                }
            }
        }
        
        KeyboardMatrix keyboardMatrix = jvicRunner.getKeyboardMatrix();
        
        if ((previousDirection != 0) && (direction != previousDirection)) {
            keyboardMatrix.keyUp(DIRECTION_TO_KEY_MAP[previousDirection]);
        }
        if ((direction != 0) && (direction != previousDirection)) {
            keyboardMatrix.keyDown(DIRECTION_TO_KEY_MAP[direction]);
        }
                
        previousDirection = direction;
    }

    /**
     * Toggles the display of the FPS.
     */
    public void toggleShowFPS() {
        showFPS = !showFPS;
    }

    /**
     * Saves a screenshot of the machine's current screen contents.
     */
    public void saveScreenshot() {
        jvicRunner.saveScreenshot(screenPixmap, appConfigItem);
    }

    @Override
    public void resize(int width, int height) {
        ensureActiveScreenResources();

        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
            
            // Screen size reverts back to FIT whenever in portrait mode.
            machineInputProcessor.setScreenSize(ScreenSize.FIT);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
        
        machineInputProcessor.adjustWorldMinMax(width, height, machineType);
        
        viewport.update(width, height, false);

        // Align VIC screen's top edge to top of the viewport.
        int screenWidth = ((machineType.getVisibleScreenHeight() / 3) * 4);
        int screenHeight = machineType.getVisibleScreenHeight();
        Camera camera = viewport.getCamera();
        camera.position.x = screenWidth / 2;
        camera.position.y = screenHeight - viewport.getWorldHeight() / 2;
        camera.update();

        machineInputProcessor.resize(width, height);
        viewportManager.update(width, height);
    }

    @Override
    public void pause() {
        // On Android, this is also called when the "Home" button is pressed.
        jvicRunner.pause();
    }

    @Override
    public void resume() {
        KeyboardType.init();
        jvicRunner.resume();
    }

    @Override
    public void show() {
        KeyboardType.init();
        
        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
        
        jvicRunner.resume();
        
        if (appConfigItem != null) {
            jvicRunner.start(appConfigItem);
        }
    }

    @Override
    public void hide() {
        // On Android, this is also called when the "Back" button is pressed.
        KeyboardType.dispose();
    }

    @Override
    public void dispose() {
        KeyboardType.dispose();
        keyboardIcon.dispose();
        joystickIcon.dispose();
        backIcon.dispose();
        fullScreenIcon.dispose();
        muteIcon.dispose();
        unmuteIcon.dispose();
        playIcon.dispose();
        pauseIcon.dispose();
        screenSizeIcon.dispose();
        warpSpeedIcon.dispose();
        cameraIcon.dispose();
        if (softFilterShader != null) {
            softFilterShader.dispose();
        }
        batch.dispose();
        jvicRunner.stop();
        disposeScreens();
    }

    /**
     * Disposes the libGDX screen resources for each MachineType.
     */
    private void disposeScreens() {
        for (Pixmap pixmap : machineTypePixmaps.values()) {
            pixmap.dispose();
        }
        for (Texture[] screens : machineTypeTextures.values()) {
            screens[0].dispose();
            screens[1].dispose();
            screens[2].dispose();
            screens[3].dispose();
            screens[4].dispose();
            screens[5].dispose();
        }
    }

    /**
     * Changes the blur mode, to either be on or off.
     * 
     * @param blurOn
     */
    public void changeBlur(boolean blurOn) {
        setTextureFilterMode(blurOn ? "linear" : "nearest");
    }

    public void changeTextureFilter(String textureFilter) {
        setTextureFilterMode(textureFilter);
    }

    private void setTextureFilterMode(String textureFilter) {
        if (textureFilter == null) {
            screenFilterMode = ScreenFilterMode.SOFT;
            softFilterBlend = SOFT_FILTER_BLEND;
            return;
        }

        String normalizedFilter = textureFilter.trim();
        Float numericBlend = parseSoftFilterBlend(normalizedFilter);
        if (numericBlend != null) {
            screenFilterMode = ScreenFilterMode.SOFT;
            softFilterBlend = numericBlend;
        } else if ("linear".equalsIgnoreCase(normalizedFilter)) {
            screenFilterMode = ScreenFilterMode.LINEAR;
            softFilterBlend = SOFT_FILTER_BLEND;
        } else if ("nearest".equalsIgnoreCase(normalizedFilter)) {
            screenFilterMode = ScreenFilterMode.NEAREST;
            softFilterBlend = SOFT_FILTER_BLEND;
        } else {
            screenFilterMode = ScreenFilterMode.SOFT;
            softFilterBlend = SOFT_FILTER_BLEND;
        }
    }

    private Float parseSoftFilterBlend(String textureFilter) {
        try {
            float blend = Float.parseFloat(textureFilter);
            if ((blend >= 0.0f) && (blend <= 1.0f)) {
                return blend;
            }
        } catch (NumberFormatException ignored) {
            // Not a numeric blend value; fall back to named filter modes.
        }
        return null;
    }

    private ScreenFilterMode getActiveScreenFilterMode() {
        if ((screenFilterMode == ScreenFilterMode.SOFT) && (softFilterShader == null)) {
            return ScreenFilterMode.LINEAR;
        }
        return screenFilterMode;
    }

    private int getTextureOffset(ScreenFilterMode filterMode) {
        return (filterMode == ScreenFilterMode.LINEAR) ? 3 : 0;
    }

    private ShaderProgram createSoftFilterShader() {
        ShaderProgram shader = new ShaderProgram(SOFT_FILTER_VERTEX_SHADER, SOFT_FILTER_FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            Gdx.app.error("MachineScreen", "Unable to compile soft filter shader: " + shader.getLog());
            shader.dispose();
            return null;
        }
        return shader;
    }

    /**
     * Gets the JVicRunner implementation instance that is running the VIC game.
     * 
     * @return
     */
    public JVicRunner getJvicRunner() {
        return jvicRunner;
    }

    public MachineInputProcessor getMachineInputProcessor() {
        return machineInputProcessor;
    }
    
    public ExtendViewport getViewport() {
        ensureActiveScreenResources();
        return viewport;
    }
    
    public MachineType getMachineType() {
        return machineType;
    }
    
    public JVic getJVic() {
        return jvic;
    }
    
    /**
     * Returns user to the Home screen.
     */
    public void exit() {
        jvic.setScreen(jvic.getHomeScreen());
    }
}
