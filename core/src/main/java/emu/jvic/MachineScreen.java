package emu.jvic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
//import com.badlogic.gdx.graphics.PixmapIO;
//import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Base64Coder;
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

        machineTypePixmaps = new HashMap<MachineType, Pixmap>();
        machineTypeTextures = new HashMap<MachineType, Texture[]>();
        machineTypeViewports = new HashMap<MachineType, ExtendViewport>();
        machineTypeCameras = new HashMap<MachineType, Camera>();

        createScreenResourcesForMachineType(MachineType.PAL);
        createScreenResourcesForMachineType(MachineType.NTSC);

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

        // TODO: Review if this is needed.
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
        return touchpad;
    }
    
    private Image createFireButton(int size) {
        Image image = new Image(new Texture("png/joystick_button.png"));
        image.setBounds(15, 15, size, size);
        image.addListener(fireButtonClickListener);
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
        machineType = MachineType.valueOf(appConfigItem.getMachineType());
        screenPixmap = machineTypePixmaps.get(machineType);
        screens = machineTypeTextures.get(machineType);
        camera = machineTypeCameras.get(machineType);
        viewport = machineTypeViewports.get(machineType);

        drawScreen = 1;
        updateScreen = 0;
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
        Texture[] screens = new Texture[3];
        screens[0] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[0].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[1] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[1].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[2] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[2].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        
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
            // TODO: See if we can check if there is a new frame ready before copying??
            copyPixels();
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
        jvicRunner.updatePixmap(screenPixmap);
        screens[updateScreen].draw(screenPixmap, 0, 0);
        updateScreen = (updateScreen + 1) % 3;
        drawScreen = (drawScreen + 1) % 3;
        return true;
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
                if (joystickAlignment.equals(JoystickAlignment.LEFT)) {
                    cameraXOffset *= -1;
                }
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
        camera.position.set((renderWidth / 2) + cameraXOffset, (renderHeight / 2) - cameraYOffset, 0.0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        batch.begin();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, 1f);
        
        // Texture isn't always drawn to match physical pixels.
        batch.draw(
                screens[drawScreen], 
                0, 0, renderWidth, renderHeight,
                machineType.getHorizontalOffset(), machineType.getVerticalOffset(), 
                machineType.getVisibleScreenWidth(), machineType.getVisibleScreenHeight(), 
                false, false);
        batch.end();

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
                    // Normal landscape.
                    batch.draw(speakerIcon,   16, viewportManager.getHeight() - 112);
                    batch.draw(pausePlayIcon, 16, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    // Free slot.
                    batch.draw(keyboardIcon,  16, 0);
                    batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, viewportManager.getHeight() - 112);
                    batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    batch.draw(joystickIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                    batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
                }
            } else if (cameraXOffset < 0) {
                // Left
                batch.draw(fullScreenIcon, 16, (viewportManager.getHeight() - 112));
                batch.draw(screenSizeIcon, 16, (viewportManager.getHeight() - (viewportManager.getHeight() / 6)) - 80);
                batch.draw(speakerIcon,    16, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                batch.draw(pausePlayIcon,  16, (viewportManager.getHeight() / 2) - 48);
                batch.draw(keyboardIcon,   16, (viewportManager.getHeight() / 3) - 32);
                batch.draw(joystickIcon,   16, (viewportManager.getHeight() / 6) - 16);
                batch.draw(backIcon,       16, 16);
            } else if (cameraXOffset > 0) {
                // Right
                batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - 112));
                batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 6)) - 80);
                batch.draw(speakerIcon,    viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                batch.draw(pausePlayIcon,  viewportManager.getWidth() - 112, (viewportManager.getHeight() / 2) - 48);
                batch.draw(keyboardIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                batch.draw(joystickIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 6) - 16);
                batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
            }
        }
        
        batch.end();
        
        // The joystick touch pad is updated and rendered via the Stage.
        if (!joystickAlignment.equals(JoystickAlignment.OFF)) {
            float joyX = 0;
            float joyY = 0;
            if (viewportManager.isPortrait()) {
                // Top of keyboard is: 765 + 135 = 900.
                int joyWidth = 200;
                int agiScreenBase = viewportManager.getVICScreenBase();
                int midBetweenKeybAndPic = ((agiScreenBase + 900) / 2);
                portraitTouchpad.setSize(joyWidth, joyWidth);
                portraitTouchpad.setY(midBetweenKeybAndPic - (joyWidth / 2));
                portraitFireButton.setSize(joyWidth, joyWidth);
                portraitFireButton.setY(midBetweenKeybAndPic - (joyWidth / 2));
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
                if ((viewportManager.getVICScreenBase() > 0) || (sidePaddingWidth <= 64)) {
                    int joyWidth = Math.max(Math.min(140 + viewportManager.getVICScreenBase(), 216), 140);
                    landscapeTouchpad.setSize(joyWidth, joyWidth);
                    landscapeTouchpad.setY(16);
                    landscapeTouchpad.setX(viewportManager.getWidth() - joyWidth - 16);
                    landscapeTouchpad.getStyle().knob.setMinHeight(joyWidth * 0.6f);
                    landscapeTouchpad.getStyle().knob.setMinWidth(joyWidth * 0.6f);
                    landscapeStage.act(delta);
                    landscapeStage.draw();
                    joyX = landscapeTouchpad.getKnobPercentX();
                    joyY = landscapeTouchpad.getKnobPercentY();
                } else {
                    float joyWidth = Math.min(Math.max((sidePaddingWidth * 2) - 32, 96), 200);
                    landscapeTouchpad.setSize(joyWidth, joyWidth);
                    landscapeTouchpad.getStyle().knob.setMinHeight(joyWidth * 0.6f);
                    landscapeTouchpad.getStyle().knob.setMinWidth(joyWidth * 0.6f);
                    landscapeTouchpad.setY(viewportManager.getHeight() - (viewportManager.getHeight() / 2) - (joyWidth / 2));
                    switch (joystickAlignment) {
                        case OFF:
                            break;
                        case RIGHT:
                            landscapeTouchpad.setX(1920 - joyWidth - 16);
                            break;
                        case LEFT:
                            landscapeTouchpad.setX(16);
                            break;
                    }
                    landscapeStage.act(delta);
                    landscapeStage.draw();
                    joyX = landscapeTouchpad.getKnobPercentX();
                    joyY = landscapeTouchpad.getKnobPercentY();
                }
            }
            processJoystickInput(joyX, joyY);
        }
    }
    
    public ClickListener fireButtonClickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
            KeyboardMatrix keyboardMatrix = jvicRunner.getKeyboardMatrix();
            keyboardMatrix.keyDown(Keys.INSERT);
            keyboardMatrix.keyUp(Keys.INSERT);
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
            // Convert heading to an AGI direction.
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
        /*
        String friendlyAppName = appConfigItem != null ? appConfigItem.getName().replaceAll("[ ,\n/\\:;*?\"<>|!]", "_")
                : "shot";
        if (Gdx.app.getType().equals(ApplicationType.Desktop)) {
            try {
                StringBuilder filePath = new StringBuilder("jvic_screens/");
                filePath.append(friendlyAppName);
                filePath.append("_");
                filePath.append(System.currentTimeMillis());
                filePath.append(".png");
                
                ScreenSize currentScreenSize = machineInputProcessor.getScreenSize();
                int renderWidth = currentScreenSize.getRenderWidth(machineType);
                int renderHeight = currentScreenSize.getRenderHeight(machineType);
                Pixmap pixmap = new Pixmap(renderWidth, renderHeight, Pixmap.Format.RGBA8888);
                pixmap.drawPixmap(
                        screenPixmap, 
                        machineType.getHorizontalOffset(), machineType.getVerticalOffset(),
                        machineType.getVisibleScreenWidth(), machineType.getVisibleScreenHeight(),
                        0, 0, renderWidth, renderHeight);
                
                // TODO: Move to platform specific code, as not supported by GWT/HTML5
                PixmapIO.writePNG(Gdx.files.external(filePath.toString()), pixmap);
            } catch (Exception e) {
                // Ignore.
            }
        }
        
        // TODO: Move to platform specific code, as not supported by GWT/HTML5
        if (appConfigItem != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PNG writer = new PNG((int) (screenPixmap.getWidth() * screenPixmap.getHeight() * 1.5f));
                try {
                    writer.setFlipY(false);
                    writer.write(out, screenPixmap);
                } finally {
                    writer.dispose();
                }
                jvic.getScreenshotStore().putString(friendlyAppName, new String(Base64Coder.encode(out.toByteArray())));
                jvic.getScreenshotStore().flush();
            } catch (IOException ex) {
                // Ignore.
            }
        }
        */
    }

    @Override
    public void resize(int width, int height) {
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
        }
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
        return viewport;
    }
    
    public MachineType getMachineType() {
        return machineType;
    }
    
    /**
     * Returns user to the Home screen.
     */
    public void exit() {
        jvic.setScreen(jvic.getHomeScreen());
    }
}
