package emu.jvic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jvic.config.AppConfigItem;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.MachineInputProcessor;
import emu.jvic.ui.ViewportManager;

/**
 * The main screen in the JVic emulator, i.e. the one that shows the video output of the VIC.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

    /**
     * The Game object for JVicGdx. Allows us to easily change screens.
     */
    private JVic jvic;

    /**
     * This represents the VIC 20 machine.
     */
    private Machine machine;

    /**
     * The Thread that updates the machine at the expected rate.
     */
    private MachineRunnable machineRunnable;

    /**
     * The InputProcessor for the MachineScreen. Handles the key and touch input.
     */
    private MachineInputProcessor machineInputProcessor;

    /**
     * SpriteBatch shared by all rendered components.
     */
    private SpriteBatch batch;

    // Currently in use components to support rendering of the VIC 20 screen. The
    // objects that these references point to will change depending on the MachineType.
    private Pixmap screenPixmap;
    private Viewport viewport;
    private Camera camera;
    private Texture[] screens;
    private int drawScreen = 1;
    private int updateScreen = 0;

    // Screen resources for each MachineType.
    private Map<MachineType, Pixmap> machineTypePixmaps;
    private Map<MachineType, Camera> machineTypeCameras;
    private Map<MachineType, Viewport> machineTypeViewports;
    private Map<MachineType, Texture[]> machineTypeTextures;

    // UI components.
    private Texture joystickIcon;
    private Texture keyboardIcon;
    private Texture backIcon;
    private Texture warpSpeedIcon;
    private Texture cameraIcon;

    private ViewportManager viewportManager;

    // Touchpad
    private Stage portraitStage;
    private Stage landscapeStage;
    private Touchpad portraitTouchpad;
    private Touchpad landscapeTouchpad;

    // FPS text font
    private BitmapFont font;

    private boolean showFPS;

    /**
     * Details about the application currently running.
     */
    private AppConfigItem appConfigItem;

    /**
     * Constructor for MachineScreen.
     * 
     * @param jvic           The JVicGdx instance.
     * @param confirmHandler
     */
    public MachineScreen(JVic jvic, ConfirmHandler confirmHandler) {
        this.jvic = jvic;

        // Create the Machine, at this point not configured with a MachineType.
        this.machine = new Machine();
        this.machineRunnable = new MachineRunnable(this.machine);

        batch = new SpriteBatch();

        machineTypePixmaps = new HashMap<MachineType, Pixmap>();
        machineTypeTextures = new HashMap<MachineType, Texture[]>();
        machineTypeViewports = new HashMap<MachineType, Viewport>();
        machineTypeCameras = new HashMap<MachineType, Camera>();

        createScreenResourcesForMachineType(MachineType.PAL);
        createScreenResourcesForMachineType(MachineType.NTSC);

        keyboardIcon = new Texture("png/keyboard_icon.png");
        joystickIcon = new Texture("png/joystick_icon.png");
        backIcon = new Texture("png/back_arrow.png");
        warpSpeedIcon = new Texture("png/warp_speed_icon.png");
        cameraIcon = new Texture("png/camera_icon.png");

        viewportManager = ViewportManager.getInstance();

        // Create and register an input processor for keys, etc.
        machineInputProcessor = new MachineInputProcessor(this, confirmHandler);

        // FPS font
        font = new BitmapFont(Gdx.files.internal("data/default.fnt"), false);
        font.setFixedWidthGlyphs(".  *"); // Note: The * and . are ignored, first and last. Only the space is fixed width.
        font.setColor(new Color(0x808080FF));
        font.getData().setScale(2f, 2f);

        // Start up the MachineRunnable Thread. It will initially be paused, awaiting
        // machine configuration.
        Thread machineThread = new Thread(this.machineRunnable);
        machineThread.start();
    }

    /**
     * Initialises the Machine with the given AppConfigItem. This will represent an
     * app that was selected on the HomeScreen. As part of this initialisation, it
     * creates the Pixmap, screen Textures, Camera and Viewport required to render
     * the VIC 20 screen at the size needed for the MachineType being emulatoed.
     * 
     * @param appConfigItem The configuration for the app that was selected on the HomeScreen.
     */
    public void initMachine(AppConfigItem appConfigItem) {
        this.appConfigItem = appConfigItem;

        if (appConfigItem.getFileType().equals("")) {
            // If there is no file type, there is no file to load and we simply boot in to BASIC.
            machine.init(appConfigItem.getRam(), appConfigItem.getMachineType());
        } else {
            // Otherwise there is a file to load.
            machine.init(appConfigItem.getFilePath(), appConfigItem.getFileType(), appConfigItem.getMachineType(),
                    appConfigItem.getRam());
        }

        // Switch libGDX screen resources used by the VIC 20 screen to the size required
        // by the MachineType.
        screenPixmap = machineTypePixmaps.get(appConfigItem.getMachineType());
        screens = machineTypeTextures.get(appConfigItem.getMachineType());
        camera = machineTypeCameras.get(appConfigItem.getMachineType());
        viewport = machineTypeViewports.get(appConfigItem.getMachineType());

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
                Pixmap.Format.RGB565);
        Texture[] screens = new Texture[3];
        screens[0] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
        screens[0].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[1] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
        screens[1].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[2] = new Texture(screenPixmap, Pixmap.Format.RGB565, false);
        screens[2].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        Camera camera = new OrthographicCamera();
        Viewport viewport = new ExtendViewport((machineType.getVisibleScreenHeight() / 3) * 4,
                machineType.getVisibleScreenHeight(), camera);

        machineTypePixmaps.put(machineType, screenPixmap);
        machineTypeTextures.put(machineType, screens);
        machineTypeCameras.put(machineType, camera);
        machineTypeViewports.put(machineType, viewport);
    }

    private long lastLogTime;
    private long avgRenderTime;
    private long avgDrawTime;
    private long renderCount;
    private long drawCount;

    @Override
    public void render(float delta) {
        long renderStartTime = TimeUtils.nanoTime();
        long fps = Gdx.graphics.getFramesPerSecond();
        long maxFrameDuration = (long) (1000000000L * (fps == 0 ? 0.016667f : delta));
        boolean draw = false;

        if (machine.isPaused()) {
            // When paused, we limit the draw frequency since there isn't anything to change.
            draw = ((fps < 30) || ((renderCount % (fps / 30)) == 0));

        } else {
            // Check if the Machine has a frame ready to be displayed.
            short[] framePixels = machine.getFramePixels();
            if (framePixels != null) {
                // If it does then update the Texture on the GPU.
                BufferUtils.copy(framePixels, 0, screenPixmap.getPixels(),
                        machine.getMachineType().getTotalScreenWidth()
                                * machine.getMachineType().getTotalScreenHeight());
                screens[updateScreen].draw(screenPixmap, 0, 0);
                updateScreen = (updateScreen + 1) % 3;
                drawScreen = (drawScreen + 1) % 3;
            }

            draw = true;
        }

        if (draw) {
            drawCount++;
            draw();
            long drawDuration = TimeUtils.nanoTime() - renderStartTime;
            if (renderCount == 0) {
                avgDrawTime = drawDuration;
            } else {
                avgDrawTime = ((avgDrawTime * renderCount) + drawDuration) / (renderCount + 1);
            }
        }

        long renderDuration = TimeUtils.nanoTime() - renderStartTime;
        if (renderCount == 0) {
            avgRenderTime = renderDuration;
        } else {
            avgRenderTime = ((avgRenderTime * renderCount) + renderDuration) / (renderCount + 1);
        }

        renderCount++;

        if ((lastLogTime == 0) || (renderStartTime - lastLogTime > 10000000000L)) {
            lastLogTime = renderStartTime;
            // Gdx.app.log("RenderTime", String.format(
            // "[%d] avgDrawTime: %d avgRenderTime: %d maxFrameDuration: %d delta: %f fps:
            // %d",
            // drawCount, avgDrawTime, avgRenderTime, maxFrameDuration, delta,
            // Gdx.graphics.getFramesPerSecond()));
        }
    }

    private void draw() {
        // Get the KeyboardType currently being used by the MachineScreenProcessor.
        KeyboardType keyboardType = machineInputProcessor.getKeyboardType();

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Render the VIC screen.
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        batch.begin();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, 1f);
        batch.draw(screens[drawScreen], 0, 0, machine.getScreenWidth(), machine.getScreenHeight(),
                machine.getScreenLeft(), machine.getScreenTop(), machine.getMachineType().getVisibleScreenWidth(),
                machine.getMachineType().getVisibleScreenHeight(), false, false);
        batch.end();

        // Render the UI elements, e.g. the keyboard and joystick icons.
        viewportManager.getCurrentCamera().update();
        batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
        batch.enableBlending();
        batch.begin();
        if (keyboardType.equals(KeyboardType.JOYSTICK)) {
            if (viewportManager.isPortrait()) {
                batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0);
                batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
            } else {
                batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0, 201, 201);
                batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
            }
        } else if (keyboardType.isRendered()) {
            batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
            batch.draw(keyboardType.getTexture(), 0, keyboardType.getRenderOffset());
        } else if (keyboardType.equals(KeyboardType.OFF)) {
            // The keyboard and joystick icons are rendered only when an input type isn't
            // showing.
            batch.setColor(c.r, c.g, c.b, 0.5f);
            if (viewportManager.isPortrait()) {
                batch.draw(joystickIcon, 0, 0);
                if (Gdx.app.getType().equals(ApplicationType.Android)) {
                    // Main Oric keyboard on the right.
                    batch.draw(keyboardIcon, viewportManager.getWidth() - 145, 0);
                    // Mobile keyboard for debug purpose. Wouldn't normally make this available.
                    batch.setColor(c.r, c.g, c.b, 0.15f);
                    batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth() / 2 - 70, 0);

                } else {
                    // Desktop puts keyboard button in the middle.
                    batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth() / 2 - 70, 0);
                    // and the back button on the right.
                    batch.draw(backIcon, viewportManager.getWidth() - 145, 0);
                }

            } else {
                batch.draw(joystickIcon, 0, viewportManager.getHeight() - 140);
                batch.draw(keyboardIcon, viewportManager.getWidth() - 150, viewportManager.getHeight() - 125);
                batch.draw(backIcon, viewportManager.getWidth() - 150, 0);
            }
        }
        float screenHeight = (viewportManager.getHeight() - (viewportManager.getWidth() / 4) * 3);
        if (machineRunnable.isWarpSpeed()) {
            batch.setColor(0.5f, 1.0f, 0.5f, 1.0f);
        } else {
            batch.setColor(c.r, c.g, c.b, 0.5f);
        }
        batch.draw(warpSpeedIcon, 0, screenHeight - 128);
        batch.setColor(c.r, c.g, c.b, 0.5f);
        batch.draw(cameraIcon, viewportManager.getWidth() - 145, screenHeight - 128);
        if (showFPS) {
            font.draw(batch, String.format("%4dFPS", machineRunnable.getFramesLastSecond()),
                    viewportManager.getWidth() - viewportManager.getWidth() / 2 - 90, screenHeight - 48);
        }
        batch.end();
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
        String friendlyAppName = appConfigItem != null ? appConfigItem.getName().replaceAll("[ ,\n/\\:;*?\"<>|!]", "_")
                : "shot";
        if (Gdx.app.getType().equals(ApplicationType.Desktop)) {
            try {
                StringBuilder filePath = new StringBuilder("jvic_screens/");
                filePath.append(friendlyAppName);
                filePath.append("_");
                filePath.append(System.currentTimeMillis());
                filePath.append(".png");
                PixmapIO.writePNG(Gdx.files.external(filePath.toString()), screenPixmap);
            } catch (Exception e) {
                // Ignore.
            }
        }
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
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);

        // Align VIC screen's top edge to top of the viewport.
        Camera camera = viewport.getCamera();
        camera.position.x = machine.getScreenWidth() / 2;
        camera.position.y = machine.getScreenHeight() - viewport.getWorldHeight() / 2;
        camera.update();

        machineInputProcessor.resize(width, height);
        viewportManager.update(width, height);
    }

    @Override
    public void pause() {
        // On Android, this is also called when the "Home" button is pressed.
        machineRunnable.pause();
    }

    @Override
    public void resume() {
        KeyboardType.init();
        machineRunnable.resume();
    }

    @Override
    public void show() {
        // Note that this screen should not be shown unless the Machine has been
        // initialised by calling
        // the initMachine method of MachineScreen. This will create the necessary
        // PixMap and Textures
        // required for the MachineType.
        KeyboardType.init();
        Gdx.input.setInputProcessor(machineInputProcessor);
        machineRunnable.resume();
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
        warpSpeedIcon.dispose();
        cameraIcon.dispose();
        batch.dispose();
        machineRunnable.stop();
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
     * Gets the Machine that this MachineScreen is running.
     * 
     * @return The Machine that this MachineScreen is running.
     */
    public Machine getMachine() {
        return machine;
    }

    /**
     * Gets the MachineRunnable that is running the Machine.
     * 
     * @return The MachineRunnable that is running the Machine.
     */
    public MachineRunnable getMachineRunnable() {
        return machineRunnable;
    }

    /**
     * Returns user to the Home screen.
     */
    public void exit() {
        jvic.setScreen(jvic.getHomeScreen());
    }
}
