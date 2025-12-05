package emu.jvic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jvic.config.AppConfig;
import emu.jvic.config.AppConfigItem;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.OpenFileResponseHandler;
import emu.jvic.ui.PagedScrollPane;
import emu.jvic.ui.PaginationWidget;
import emu.jvic.ui.TextInputResponseHandler;
import emu.jvic.ui.ViewportManager;

/**
 * The Home screen of the JVic emulator, i.e. the one that shows all of the
 * available boot options and programs to load. Reminiscent of the Android home
 * screen.
 * 
 * @author Lance Ewing
 */
public class HomeScreen extends InputAdapter implements Screen {

    /**
     * The Game object for JVic. Allows us to easily change screens.
     */
    private JVic jvic;

    private Skin skin;
    private Stage portraitStage;
    private Stage landscapeStage;
    private ViewportManager viewportManager;
    private Map<String, AppConfigItem> appConfigMap;
    private Map<String, Texture> buttonTextureMap;
    private Texture backgroundLandscape;
    private Texture backgroundPortrait;
    private PaginationWidget portraitPaginationWidget;
    private PaginationWidget landscapePaginationWidget;
    private Texture titleTexture;

    /**
     * Invoked by JVic whenever it would like to show a dialog, such as when it
     * needs the user to confirm an action, or to choose a file.
     */
    private DialogHandler dialogHandler;

    /**
     * The InputProcessor for the Home screen. This is an InputMultiplexor, which
     * includes both the Stage and the HomeScreen.
     */
    private InputMultiplexer portraitInputProcessor;
    private InputMultiplexer landscapeInputProcessor;

    /**
     * Holds a reference to the special app config item for BASIC.
     */
    private AppConfigItem basicAppConfigItem;
    
    /**
     * Holds a reference to the AppConfigItem for the last program that was
     * launched.
     */
    private AppConfigItem lastProgramLaunched;

    /**
     * The JVic version, read from version.txt
     */
    private String version;
    
    /**
     * The timestamp of the last key press. Supports searching for games.
     */
    private long lastKeyPress;
    
    /**
     * The current search string for page navigation.
     */
    private String searchString = "";;
    
    /**
     * Constructor for HomeScreen.
     * 
     * @param jvic The JVic instance.
     * @param dialogHandler
     */
    public HomeScreen(JVic jvic, DialogHandler dialogHandler) {
        this.jvic = jvic;
        this.dialogHandler = dialogHandler;
 
        // Read JVic's current version.
        version = Gdx.files.internal("data/version.txt").readString();
        
        // Load the app meta data.
        Json json = new Json();
        String appConfigJson = Gdx.files.internal("data/programs.json").readString();
        AppConfig appConfig = json.fromJson(AppConfig.class, appConfigJson);
        //removeProgramsWithIcons(appConfig);
        basicAppConfigItem = buildBasicAppConfigItem();
        appConfigMap = new TreeMap<String, AppConfigItem>();
        for (AppConfigItem appConfigItem : appConfig.getApps()) {
            appConfigMap.put(appConfigItem.getName(), appConfigItem);
        }
        
        buttonTextureMap = new HashMap<String, Texture>();
        skin = new Skin(Gdx.files.internal("data/uiskin.json"));
        skin.add("top", skin.newDrawable("default-round", new Color(0, 0, 0, 0)), Drawable.class);
        skin.add("empty", skin.newDrawable("default-round", new Color(1f, 1f, 1f, 0.1f)), Drawable.class);

        titleTexture = new Texture("png/jvic_title.png");
        backgroundLandscape = new Texture("jpg/landscape_background.jpg");
        backgroundLandscape.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        backgroundPortrait = new Texture("jpg/portrait_background.jpg");
        backgroundPortrait.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        viewportManager = ViewportManager.getInstance();
        portraitPaginationWidget = new PaginationWidget(this, 1080);
        landscapePaginationWidget = new PaginationWidget(this, 1920);
        portraitStage = createStage(viewportManager.getPortraitViewport(), portraitPaginationWidget, appConfig);
        landscapeStage = createStage(viewportManager.getLandscapeViewport(), landscapePaginationWidget, appConfig);

        // The stage handles most of the input, but we need to handle the BACK button
        // separately.
        portraitInputProcessor = new InputMultiplexer();
        portraitInputProcessor.addProcessor(portraitStage);
        portraitInputProcessor.addProcessor(this);
        landscapeInputProcessor = new InputMultiplexer();
        landscapeInputProcessor.addProcessor(landscapeStage);
        landscapeInputProcessor.addProcessor(this);
    }
    
    /**
     * Removes programs from the AppConfig where the icon path is not set.
     * 
     * @param appConfig The AppConfig to remove the programs from.
     */
    private void removeProgramsWithIcons(AppConfig appConfig) {
        if ((appConfig != null) && (appConfig.getApps() != null)) {
            ArrayList<AppConfigItem> modifiedApps = new ArrayList<>();
            for (AppConfigItem appConfigItem : appConfig.getApps()) {
                if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
                    modifiedApps.add(appConfigItem);
                }
            }
            appConfig.setApps(modifiedApps);
        }
    }
    
    private AppConfigItem buildBasicAppConfigItem() {
        AppConfigItem basicAppConfigItem = new AppConfigItem();
        basicAppConfigItem.setName("BASIC");
        basicAppConfigItem.setFilePath("");
        basicAppConfigItem.setFileType("");
        basicAppConfigItem.setIconPath("screenshots/B/Basic/Basic.png");
        basicAppConfigItem.setMachineType("PAL");
        basicAppConfigItem.setRam("RAM_UNEXPANDED");
        return basicAppConfigItem;
    }

    private Stage createStage(Viewport viewport, PaginationWidget paginationWidget, AppConfig appConfig) {
        Stage stage = new Stage(viewport);
        addAppButtonsToStage(stage, paginationWidget, appConfig);
        return stage;
    }

    private void addAppButtonsToStage(Stage stage, PaginationWidget paginationWidget, AppConfig appConfig) {
        Table container = new Table();
        stage.addActor(container);
        container.setFillParent(true);

        Table currentPage = new Table().pad(0, 0, 0, 0);
        Image title = new Image(titleTexture);
        
        float viewportWidth = viewportManager.getWidth();
        float viewportHeight = viewportManager.getHeight();
        
        // This is the padding on the left and right sides of the screen, not app buttons.
        int sidePadding = (viewportHeight > (viewportWidth / 1.25f))? 12 : 15;
        
        int availableHeight = (int)(viewportHeight - PAGINATION_HEIGHT);
        int columns = (int)((viewportWidth - (sidePadding * 2)) / ICON_IMAGE_WIDTH);
        int rows = (int)(availableHeight / (ICON_IMAGE_HEIGHT + ICON_LABEL_HEIGHT + 10));
        
        // This is the total amount of padding after removal of side padding.
        int totalHorizPadding = 0;
        
        // This is the amount of padding either side of each app button.
        int horizPaddingUnit = 0;

        Button infoButton = buildButton("INFO", null, "png/info.png", 96, 96, null, null);
        currentPage.add().expandX();
        currentPage.add(infoButton).pad(30, 0, 0, 20).align(Align.right).expandX();
        currentPage.row();
        currentPage.add().expandX();
        
        if (viewportManager.isLandscape()) {
            // Landscape.
            container.setBackground(new Image(backgroundLandscape).getDrawable());
            totalHorizPadding = 1920 - (ICON_IMAGE_WIDTH * columns) - (sidePadding * 2);
            horizPaddingUnit = totalHorizPadding / (columns * 2);
            int titleWidth = 428;
            float titlePadding = ((1920 - titleWidth) / 2);
            currentPage.add(title).width(titleWidth).height(197).pad(0, titlePadding, 0, titlePadding).expand();
        } else {
            // Portrait.
            container.setBackground(new Image(backgroundPortrait).getDrawable());
            totalHorizPadding = 1080 - (ICON_IMAGE_WIDTH * columns) - (sidePadding * 2);
            horizPaddingUnit = totalHorizPadding / (columns * 2);
            int titleWidth = 428;
            float titlePadding = ((1080 - titleWidth) / 2);
            currentPage.add(title).width(titleWidth).height(197).pad(0, titlePadding, 0, titlePadding).expand();
        }
        
        Button addButton = buildButton("ADD", null, "png/open_file.png", 96, 96, null, null);
        currentPage.row();
        currentPage.add().expandX();
        currentPage.add(addButton).pad(0, 0, 30, 20).align(Align.right).expandX();
        
        PagedScrollPane pagedScrollPane = new PagedScrollPane();
        pagedScrollPane.setHomeScreen(this);
        pagedScrollPane.setFlingTime(0.01f);

        int itemsPerPage = columns * rows;
        int pageItemCount = 0;

        // Set up first page, which is mainly empty.
        pagedScrollPane.addPage(currentPage);
        
        currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
        currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);

        // Add entry at the start for BASIC that will always be present
        currentPage.add(buildAppButton(basicAppConfigItem)).expand().fill();
        pageItemCount++;
        
        for (AppConfigItem appConfigItem : appConfig.getApps()) {
            // Every itemsPerPage apps, add a new page.
            if (pageItemCount == itemsPerPage) {
                pagedScrollPane.addPage(currentPage);
                pageItemCount = 0;
                currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
                currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);
            }

            // Every number of columns apps, add a new row to the current page.
            if ((pageItemCount % columns) == 0) {
                currentPage.row();
            }

            // Currently, we're using the presence of an icon path to decide whether to add it.
            //if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
                currentPage.add(buildAppButton(appConfigItem)).expand().fill();
                pageItemCount++;
            //}
        }

        // Add the last page of apps.
        if (pageItemCount <= itemsPerPage) {
            AppConfigItem appConfigItem = new AppConfigItem();
            for (int i = pageItemCount; i < itemsPerPage; i++) {
                if ((i % columns) == 0) {
                    currentPage.row();
                }
                currentPage.add(buildAppButton(appConfigItem)).expand().fill();
            }
            pagedScrollPane.addPage(currentPage);
            if (pageItemCount == itemsPerPage) {
                currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
                currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);
                for (int i = 0; i < itemsPerPage; i++) {
                    if ((i % columns) == 0) {
                        currentPage.row();
                    }
                    currentPage.add(buildAppButton(appConfigItem)).expand().fill();
                }
                pagedScrollPane.addPage(currentPage);
            }
        }

        container.add(pagedScrollPane).expand().fill();
        
        container.row();
        container.add(paginationWidget).maxHeight(PAGINATION_HEIGHT).minHeight(PAGINATION_HEIGHT);
        stage.addActor(paginationWidget);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(portraitInputProcessor);
        if (!Gdx.app.getType().equals(ApplicationType.WebGL)) {
            Gdx.graphics.setTitle("JVic");
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (viewportManager.isPortrait()) {
            portraitStage.act(delta);
            portraitStage.draw();
        } else {
            landscapeStage.act(delta);
            landscapeStage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewportManager.update(width, height);
        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
        updateHomeScreenButtonStages();
        
        // Screen is resized after returning from MachineScreen, so we need to scroll
        // back to the page that the program that was running was on.
        if (lastProgramLaunched != null) {
            showProgramPage(lastProgramLaunched, true);
            lastProgramLaunched = null;
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        portraitStage.dispose();
        landscapeStage.dispose();
        skin.dispose();
        disposeButtonTextureMap();
        landscapePaginationWidget.dispose();
        portraitPaginationWidget.dispose();
        titleTexture.dispose();
    }
    
    private void disposeButtonTextureMap() {
        if ((buttonTextureMap != null) && (!buttonTextureMap.isEmpty())) {
            for (Texture texture : buttonTextureMap.values()) {
                texture.dispose();
            }
            buttonTextureMap.clear();
        }
    }

    /**
     * Called when a key was released
     * 
     * @param keycode one of the constants in {@link Input.Keys}
     * 
     * @return whether the input was processed
     */
    public boolean keyUp(int keycode) {
        boolean modifierDown = 
                Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) ||
                Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT) ||
                Gdx.input.isKeyPressed(Keys.CONTROL_LEFT) ||
                Gdx.input.isKeyPressed(Keys.CONTROL_RIGHT) ||
                Gdx.input.isKeyPressed(Keys.ALT_LEFT) ||
                Gdx.input.isKeyPressed(Keys.ALT_RIGHT);
        
        float pageWidth = 0.0f;
        PagedScrollPane pagedScrollPane = null;
        if (viewportManager.isPortrait()) {
            Table table = (Table)portraitStage.getActors().get(0);
            pagedScrollPane = (PagedScrollPane)table.getChild(0);
            pageWidth = 1130.0f;
        }
        else {
            Table table = (Table)landscapeStage.getActors().get(0);
            pagedScrollPane = (PagedScrollPane)table.getChild(0);
            pageWidth = 1970.0f;
        }
        
        if (keycode == Keys.BACK) {
            if (Gdx.app.getType().equals(ApplicationType.Android)) {
                dialogHandler.confirm("Do you really want to Exit?", new ConfirmResponseHandler() {
                    public void yes() {
                        // Pressing BACK from the home screen will leave AGILE on Android.
                        Gdx.app.exit();
                    }

                    public void no() {
                        // Ignore. We're staying on the Home screen.
                    }
                });
                return true;
            }
        }
        else if (!modifierDown && !dialogHandler.isDialogOpen()) {
            if (keycode == Keys.PAGE_UP) {
                if (pagedScrollPane.getCurrentPageNumber() > 1) {
                    pagedScrollPane.prevProgramPage();
                }
                float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() - pageWidth, 0, pagedScrollPane.getMaxX());
                pagedScrollPane.setScrollX(newScrollX);
                pagedScrollPane.setLastScrollX(newScrollX);
            }
            else if (keycode == Keys.PAGE_DOWN) {
                if (pagedScrollPane.getCurrentPageNumber() > 0) {
                    pagedScrollPane.nextProgramPage();
                } else {
                    pagedScrollPane.updateSelection(0, false);
                }
                float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() + pageWidth, 0, pagedScrollPane.getMaxX());
                pagedScrollPane.setScrollX(newScrollX);
                pagedScrollPane.setLastScrollX(newScrollX);
            }
            else if (keycode == Keys.HOME) {
                pagedScrollPane.setScrollX(0.0f);
                pagedScrollPane.setLastScrollX(0.0f);
                pagedScrollPane.updateSelection(0, false);
            }
            else if (keycode == Keys.END) {
                pagedScrollPane.setScrollX(pagedScrollPane.getMaxX());
                pagedScrollPane.setLastScrollX(pagedScrollPane.getMaxX());
                pagedScrollPane.updateSelection(appConfigMap.size(), false);
            }
            else if (keycode == Keys.UP) {
                pagedScrollPane.prevProgramRow();
            }
            else if (keycode == Keys.LEFT) {
                if (pagedScrollPane.getCurrentSelectionIndex() == 0) {
                    float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() - pageWidth, 0, pagedScrollPane.getMaxX());
                    pagedScrollPane.setScrollX(newScrollX);
                    pagedScrollPane.setLastScrollX(newScrollX);
                } else {
                    pagedScrollPane.prevProgram();
                }
            }
            else if (keycode == Keys.RIGHT) {
                if (pagedScrollPane.getCurrentPageNumber() == 0) {
                    float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() + pageWidth, 0, pagedScrollPane.getMaxX());
                    pagedScrollPane.updateSelectionHighlight(0, true);
                    pagedScrollPane.setScrollX(newScrollX);
                    pagedScrollPane.setLastScrollX(newScrollX);
                } else {
                    pagedScrollPane.nextProgram();
                }
            }
            else if (keycode == Keys.DOWN) {
                if (pagedScrollPane.getCurrentPageNumber() > 0) {
                    pagedScrollPane.nextProgramRow();
                }
            }
            else if ((keycode == Keys.ENTER) || 
                    ((keycode == Keys.SPACE) && ((TimeUtils.millis() - lastKeyPress) > 1000))) {
                Button button = pagedScrollPane.getCurrentlySelectedProgramButton();
                if (button != null) {
                    String appName = button.getName();
                    if ((appName != null) && (!appName.equals(""))) {
                        final AppConfigItem appConfigItem = (appName.equals("BASIC")?
                                basicAppConfigItem : appConfigMap.get(appName));
                        if (appConfigItem != null) {
                            processProgramSelection(appConfigItem);
                        }
                    }
                }
            }
            else if (((keycode >= Keys.A) && (keycode <= Keys.Z)) || 
                     ((keycode >= Keys.NUM_0) && (keycode <= Keys.NUM_9)) ||
                     (keycode == Keys.SPACE)) {
                
                if ((TimeUtils.millis() - lastKeyPress) > 1000) {
                    searchString = "";
                }
                
                if ((keycode >= Keys.A) && (keycode <= Keys.Z)) {
                    searchString += ((char)(keycode + 36));
                }
                else if ((keycode >= Keys.NUM_0) && (keycode <= Keys.NUM_9)) {
                    searchString += ((char)(keycode + 41));
                }
                else if (keycode == Keys.SPACE) {
                    searchString += " ";
                }
                
                // Shortcut keys for accessing games that start with each letter.
                // Keys.A is 29, Keys.Z is 54. ASCII is A=65, Z=90. So we add 36.
                int gameIndex = getIndexOfFirstProgramStartingWithPrefix(searchString);
                if (gameIndex > -1) {
                    // Add one to allow for the "BASIC" icon in the first slot.
                    showProgramPage(gameIndex + 1, false);
                }
                lastKeyPress = TimeUtils.millis();
            }
        }
        return false;
    }

    /**
     * Draws and returns the icon to be used for game slots when we don't have
     * a proper screenshot icon for the identified game.
     * 
     * @param iconWidth
     * @param iconHeight
     * 
     * @return The Texture for the drawn icon.
     */
    public Texture drawEmptyIcon(int iconWidth, int iconHeight) {
        Pixmap pixmap = new Pixmap(iconWidth, iconHeight, Pixmap.Format.RGBA8888);
        Texture texture = new Texture(pixmap, Pixmap.Format.RGBA8888, false);
        pixmap.setColor(1.0f, 1.0f, 1.0f, 0.10f);
        pixmap.fill();
        texture.draw(pixmap, 0, 0);
        return texture;
    }
    
    private static final int ICON_IMAGE_WIDTH = 240;
    private static final int ICON_IMAGE_HEIGHT = 224;
    private static final int ICON_LABEL_HEIGHT = 90;
    private static final int PAGINATION_HEIGHT = 60;

    /**
     * Creates a button to represent the given AppConfigItem.
     * 
     * @param appConfigItem
     * 
     * @return
     */
    private Button buildAppButton(AppConfigItem appConfigItem) {
        return buildButton(
                appConfigItem.getName(), 
                appConfigItem.getDisplayName() != null? appConfigItem.getDisplayName() : "", 
                appConfigItem.getIconPath() != null? appConfigItem.getIconPath() : "", 
                ICON_IMAGE_WIDTH, ICON_IMAGE_HEIGHT,
                appConfigItem.getFileType(),
                appConfigItem.getGameId());
    }
    
    /**
     * Custom button that renders the debug with a thicker line.
     */
    private static class ProgramButton extends Button {
        
        public ProgramButton(Skin skin) {
            super(skin);
        }
        
        public void drawDebug (ShapeRenderer shapes) {
            if (!getDebug()) return;
            shapes.set(ShapeType.Line);
            shapes.setColor(new Color(1, 1, 1, 1.0f));
            shapes.rect(
                    getX(), 
                    getY(), 
                    getWidth(), 
                    getHeight());
            shapes.setColor(new Color(1, 1, 1, 0.7f));
            shapes.rect(
                    getX() + 1, 
                    getY() + 1, 
                    getWidth() - 2, 
                    getHeight() - 2);
        }
    }
    
    /**
     * Creates a button using the given parameters.
     * 
     * @param name 
     * @param displayName 
     * @param iconPath 
     * @param type 
     * @param gameId
     * 
     * @return The created Button.
     */
    private Button buildButton(String name, String labelText, String iconPath, int width, int height, String type, String gameId) {
        Button button = new ProgramButton(skin);
        ButtonStyle style = button.getStyle();
        style.up = style.down = null;

        // An app button can contain an optional icon.
        Image icon = null;
        
        Texture iconTexture = buttonTextureMap.get(iconPath);
        if (iconTexture == null) {
            if (!iconPath.isEmpty()) {
                try {
                    // See if there is screenshot icon in the assets folder.
                    Pixmap iconPixmap = new Pixmap(Gdx.files.internal(iconPath));
                    
                    // If there is, then it's expected to be 320x200, so we scale it to right aspect ratio.
                    Pixmap iconStretchedPixmap = new Pixmap(width, height, iconPixmap.getFormat());
                    iconStretchedPixmap.drawPixmap(iconPixmap,
                            0, 0, iconPixmap.getWidth(), iconPixmap.getHeight(),
                            0, 0, iconStretchedPixmap.getWidth(), iconStretchedPixmap.getHeight()
                    );
                    iconTexture = new Texture(iconStretchedPixmap);
                    iconPixmap.dispose();
                    iconStretchedPixmap.dispose();
                    
                    iconTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                    buttonTextureMap.put(iconPath, iconTexture);
                    icon = new Image(iconTexture);
                    icon.setAlign(Align.center);
                } catch (Exception e) {
                    icon = new Image(drawEmptyIcon(width, height));
                }
            } else {
                icon = new Image(drawEmptyIcon(width, height));
            }
        } else {
            icon = new Image(iconTexture);
            icon.setAlign(Align.center);
        }
            
        if (icon != null) {
            Container<Image> iconContainer = new Container<Image>();
            iconContainer.setActor(icon);
            iconContainer.align(Align.center);
            button.stack(new Image(skin.getDrawable("top")), iconContainer).width(width)
                    .height(height);
        }
        
        if (labelText != null) {
            button.row();
            Label label = null;
            if (labelText.trim().isEmpty()) {
                if ((gameId != null) && "ADD_GAME".equals(gameId)) {
                    label = new Label("Add Game", skin);
                } else {
                    label = new Label("", skin);
                }
                label.setColor(new Color(1f, 1f, 1f, 0.6f));
            } else {
                label = new Label(labelText, skin);
            }
            label.setFontScale(0.5f);
            label.setAlignment(Align.top);
            label.setWrap(false);
            button.add(label).width(150).height(90).padTop(10);
        }
        
        button.setName(name);
        button.addListener(appClickListener);
        return button;
    }
    
    /**
     * If there is a program in the AppConfigItem Map that has the given URI, then
     * that is returned; otherwise returns false.
     * 
     * @param programUri The URI of the program to get the AppConfigItem for, if it exists.
     * 
     * @return
     */
    public AppConfigItem getAppConfigItemByProgramUri(String programUri) {
        if (programUri.toLowerCase().equals("basic")) {
            return basicAppConfigItem;
        } else {
            for (AppConfigItem appConfigItem : appConfigMap.values()) {
                String uri = jvic.getJVicRunner().slugify(appConfigItem.getName());
                if (uri.equalsIgnoreCase(programUri)) {
                    return appConfigItem;
                }
            }
            return null;
        }
    }

    /**
     * Converts the given Map of AppConfigItems to an AppConfig instance.
     * 
     * @param appConfigMap The Map of AppConfigItems to convert.
     * 
     * @return The AppConfig.
     */
    private AppConfig convertAppConfigItemMapToAppConfig(Map<String, AppConfigItem> appConfigMap) {
        AppConfig appConfig = new AppConfig();
        for (String appName : appConfigMap.keySet()) {
            AppConfigItem item = appConfigMap.get(appName);
            if ((item.getFileType() == null) || (item.getFileType().trim().isEmpty())) {
                // BASIC start icon. Add these at the start of the JSON file.
                appConfig.getApps().add(item);
            }
        }
        for (String appName : appConfigMap.keySet()) {
            AppConfigItem item = appConfigMap.get(appName);
            if ((item.getFileType() != null) && (!item.getFileType().trim().isEmpty())) {
                // Tape or Disk file.
                appConfig.getApps().add(item);
            }
        }
        return appConfig;
    }

    /**
     * Updates the application buttons on the home screen Stages to reflect the
     * current AppConfigItem Map.
     */
    public void updateHomeScreenButtonStages() {
        AppConfig appConfig = convertAppConfigItemMapToAppConfig(appConfigMap);
        portraitStage.clear();
        landscapeStage.clear();
        addAppButtonsToStage(portraitStage, portraitPaginationWidget, appConfig);
        addAppButtonsToStage(landscapeStage, landscapePaginationWidget, appConfig);
    }

    /**
     * Handle clicking an app button. This will start the Machine and run the
     * selected app.
     */
    public ClickListener appClickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
            Actor actor = event.getListenerActor();
            String appName = actor.getName();
            if ((appName != null) && (!appName.equals(""))) {
                final AppConfigItem appConfigItem = (appName.equals("BASIC")?
                        basicAppConfigItem : appConfigMap.get(appName));
                if (appConfigItem != null) {
                    processProgramSelection(appConfigItem);
                } else if (appName.equals("INFO")) {
                    showAboutJVicDialog();
                } else if (appName.equals("ADD")) {
                    importProgram();
                }
            }
        }
    };
    
    public void processProgramSelection(AppConfigItem appConfigItem) {
        lastProgramLaunched = appConfigItem;
        MachineScreen machineScreen = jvic.getMachineScreen();
        machineScreen.initMachine(appConfigItem, true);
        jvic.setScreen(machineScreen);
    }
    
    private void showAboutJVicDialog() {
        dialogHandler.showAboutDialog(
                "JVic " + version + "\n\n" + 
                "To start, simply swipe or click to the right.\n\n" + 
                (Gdx.app.getType().equals(ApplicationType.WebGL)?
                "Or use the ?url= request parameter to point directly to a .d64 or .prg file.\n\n" + 
                "Most games are available on archive.org.\n\n" : "") + 
                "Source code:\nhttps://github.com/lanceewing/jvic\n\n",
                new TextInputResponseHandler() {
                    @Override
                    public void inputTextResult(boolean success, String button) {
                        if (success && !button.equals("OK")) {
                            // State management.
                            switch (button) {
                                case "EXPORT":
                                    exportState();
                                    break;
                                case "IMPORT":
                                    importState();
                                    break;
                                case "CLEAR":
                                    clearState();
                                    break;
                                case "RESET":
                                    resetState();
                                    break;
                                default:
                                    // Nothing to do.
                                    break;
                            }
                        }
                    }
                });
    }
    
    private void exportState() {
        
    }
    
    private void importState() {
        
    }
    
    private void clearState() {
        
    }
    
    private void resetState() {
        
    }
    
    private void importProgram() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                importProgramUsingOpenFileDialog();
            }
        });
    }
    
    private void importProgramUsingOpenFileDialog() {
        String startPath = jvic.getPreferences().getString("open_app_start_path", null);
        dialogHandler.openFileDialog("", startPath, new OpenFileResponseHandler() {
            @Override
            public void openFileResult(boolean success, String filePath, byte[] fileData) {
                if (success && (filePath != null) && (!filePath.isEmpty())) {
                    if (!Gdx.app.getType().equals(ApplicationType.WebGL)) {
                        // GWT/HTML5/WEBGL doesn't support FileHandle and doesn't need it anyway.
                        FileHandle fileHandle = new FileHandle(filePath);
                        jvic.getPreferences().putString("open_app_start_path", fileHandle.parent().path());
                        jvic.getPreferences().flush();
                    }
                    
                    AppConfigItem appConfigItem = new AppConfigItem();
                    appConfigItem.setName("Adhoc VIC Program");
                    appConfigItem.setFilePath(filePath);
                    appConfigItem.setFileType("ABSOLUTE");
                    if (filePath.toUpperCase().contains("NTSC")) {
                        appConfigItem.setMachineType("NTSC");
                    } else {
                        appConfigItem.setMachineType("PAL");
                    }
                    appConfigItem.setRam("RAM_UNEXPANDED");
                    appConfigItem.setFileData(fileData);
                    
                    processProgramSelection(appConfigItem);
                    
                } else {
                    jvic.getJVicRunner().cancelImport();
                }
            }
        });
    }
    
    private int getIndexOfFirstProgramStartingWithPrefix(String searchString) {
        int programIndex = 0;
        
        for (AppConfigItem appConfigItem : appConfigMap.values()) {
            String programName = appConfigItem.getName();
            if (programName.toUpperCase().startsWith(searchString)) {
                return programIndex;
            }
            programIndex++;
        }
        
        return -1;
    }
    
    private int getProgramIndex(AppConfigItem program) {
        int programIndex = 0;
        
        for (AppConfigItem appConfigItem : appConfigMap.values()) {
            programIndex++;
            if (appConfigItem.getName().equals(program.getName())) {
                return programIndex;
            }
        }
        
        // NOTE: BASIC will return 0, as it isn't in the Map.
        return 0;
    }
    
    private void showProgramPage(AppConfigItem appConfigItem, boolean skipScroll) {
        showProgramPage(getProgramIndex(appConfigItem), skipScroll);
    }
    
    public void showProgramPage(int programIndex, boolean skipScroll) {
        // Apply scroll X without animating, i.e. move immediately to the page.
        Stage currentStage = viewportManager.isPortrait()? portraitStage : landscapeStage;
        PagedScrollPane pagedScrollPane = (PagedScrollPane)
                ((Table)currentStage.getActors().get(0)).getChild(0);
        currentStage.act(0f);
        
        // Work out how far to move from far left to get to program's page.
        int programsPerPage = pagedScrollPane.getProgramsPerPage();
        float pageWidth = viewportManager.isPortrait()? 
                1080 + pagedScrollPane.getContentSpacing() : 
                1920 + pagedScrollPane.getContentSpacing();
        float newScrollX = pageWidth * (programIndex / programsPerPage) + pageWidth;
        
        // Set program highlight to the program with the specified index.
        pagedScrollPane.updateSelection(programIndex, false);
        
        pagedScrollPane.setScrollX(newScrollX);
        pagedScrollPane.setLastScrollX(newScrollX);
        if (skipScroll) {
            pagedScrollPane.updateVisualScroll();
        }
    }
    
    public PagedScrollPane getPagedScrollPane() {
        Stage currentStage = viewportManager.isPortrait()? portraitStage : landscapeStage;
        if (currentStage.getActors().notEmpty()) {
            return (PagedScrollPane)((Table)currentStage.getActors().get(0)).getChild(0);
        } else {
            return null;
        }
    }
    
    public boolean isMobile() {
        return jvic.getJVicRunner().isMobile();
    }
    
    public Map<String, AppConfigItem> getAppConfigMap() {
        return appConfigMap;
    }
}
