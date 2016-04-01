package emu.jvic;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
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
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jvic.config.AppConfig;
import emu.jvic.config.AppConfigItem;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.PagedScrollPane;
import emu.jvic.ui.ViewportManager;

/**
 * The Home screen of the JVic emulator, i.e. the one that shows all of the 
 * available boot options and programs to load. Reminiscent of the Android 
 * home screen.
 * 
 * @author Lance Ewing
 */
public class HomeScreen extends InputAdapter implements Screen  {

  /**
   * The Game object for JVicGdx. Allows us to easily change screens.
   */
  private JVicGdx jvic;
  
  private Skin skin;
  private Stage portraitStage;
  private Stage landscapeStage;
  private ViewportManager viewportManager;
  private Map<String, AppConfigItem> appConfigMap;
  private Map<String, Texture> buttonTextureMap;
  
  /**
   * Invoked by JVic whenever it would like the user to confirm an action.
   */
  private ConfirmHandler confirmHandler;
  
  /**
   * The InputProcessor for the Home screen. This is an InputMultiplexor, which includes 
   * both the Stage and the HomeScreen.
   */
  private InputMultiplexer portraitInputProcessor;
  private InputMultiplexer landscapeInputProcessor;
  
  /**
   * Constructor for HomeScreen.
   * 
   * @param jvic The JVicGdx instance.
   * @param confirmHandler
   */
  public HomeScreen(JVicGdx jvic, ConfirmHandler confirmHandler) {
    this.jvic = jvic;
    this.confirmHandler = confirmHandler;
    
    // Load the app meta data.
    Json json = new Json();
    AppConfig appConfig = json.fromJson(AppConfig.class, Gdx.files.internal("data/programs.json"));
    appConfigMap = new HashMap<String, AppConfigItem>();
    for (AppConfigItem appConfigItem : appConfig.getApps()) {
      appConfigMap.put(appConfigItem.getName(), appConfigItem);
    }
    
    buttonTextureMap = new HashMap<String, Texture>();
    skin = new Skin(Gdx.files.internal("data/uiskin.json"));
    skin.add("top", skin.newDrawable("default-round", Color.RED), Drawable.class);
    
    viewportManager = ViewportManager.getInstance();
    portraitStage = createStage(viewportManager.getPortraitViewport(), appConfig, 4, 5);
    landscapeStage = createStage(viewportManager.getLandscapeViewport(), appConfig, 7, 3);
    
    // The stage handles most of the input, but we need to handle the BACK button separately.
    portraitInputProcessor = new InputMultiplexer();
    portraitInputProcessor.addProcessor(portraitStage);
    portraitInputProcessor.addProcessor(this);
    landscapeInputProcessor = new InputMultiplexer();
    landscapeInputProcessor.addProcessor(landscapeStage);
    landscapeInputProcessor.addProcessor(this);
  }
  
  private Stage createStage(Viewport viewport, AppConfig appConfig, int columns, int rows) {
    Stage stage = new Stage(viewport);
    
    Table container = new Table();
    stage.addActor(container);
    container.setFillParent(true);

    PagedScrollPane scroll = new PagedScrollPane();
    scroll.setFlingTime(0.01f);
    scroll.setPageSpacing(25);
    
    int itemsPerPage = columns * rows;
    int pageItemCount = 0;
    Table currentPage = new Table().pad(50, 10, 50, 10);
    currentPage.defaults().pad(0, 50, 0, 50);
    
    for (AppConfigItem appConfigItem : appConfig.getApps()) {
      // Every itemsPerPage apps, add a new page.
      if (pageItemCount == itemsPerPage) {
        scroll.addPage(currentPage);
        pageItemCount = 0;
        currentPage = new Table().pad(50, 10, 50, 10);
        currentPage.defaults().pad(0, 50, 0, 50);
      }
      
      // Every number of columns apps, add a new row to the current page.
      if ((pageItemCount % columns) == 0) {
        currentPage.row();
      }
      
      currentPage.add(buildAppButton(appConfigItem)).expand().fill();
      
      pageItemCount++;
    }
    
    // Add the last page of apps.
    if (pageItemCount <= itemsPerPage) {
      AppConfigItem appConfigItem = new AppConfigItem();
      for (int i=pageItemCount; i<itemsPerPage; i++) {
        if ((i % columns) == 0) {
          currentPage.row();
        }
        currentPage.add(buildAppButton(appConfigItem)).expand().fill();
      }
      scroll.addPage(currentPage);
    }

    container.add(scroll).expand().fill();
    
    return stage;
  }
  
  @Override
  public void show() {
    Gdx.input.setInputProcessor(portraitInputProcessor);
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
    
    for (Texture texture: buttonTextureMap.values()) {
      texture.dispose();
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
    if (keycode == Keys.BACK) {
      if (Gdx.app.getType().equals(ApplicationType.Android)) {
        confirmHandler.confirm("Do you really want to Exit?", new ConfirmResponseHandler() {
          public void yes() {
            // Pressing BACK from the home screen will leave JVic.
            Gdx.app.exit();
          }
          public void no() {
            // Ignore. We're staying on the Home screen.
          }
        });
        return true;
      }
    }
    return false;
  }
  
  /**
   * Creates a button to represent the given AppConfigItem.
   * 
   * @param appConfigItem AppConfigItem containing details about the app to build a Button to represent.
   * 
   * @return The button to use for running the given AppConfigItem.
   */
  public Button buildAppButton(AppConfigItem appConfigItem) {
    Button button = new Button(skin);
    ButtonStyle style = button.getStyle();
    style.up =  style.down = null;
    
    // An app button can contain an optional icon.
    Image icon = null;
    if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
      Texture iconTexture = buttonTextureMap.get(appConfigItem.getIconPath());
      if (iconTexture == null) {
        iconTexture = new Texture(appConfigItem.getIconPath());
        buttonTextureMap.put(appConfigItem.getIconPath(), iconTexture);
        icon = new Image(iconTexture);
        icon.setAlign(Align.center);
      }
    }
    
    if (icon != null) {
      Container<Image> iconContainer = new Container<Image>();
      iconContainer.setActor(icon);
      iconContainer.align(Align.center);
      button.stack(new Image(skin.getDrawable("top")), iconContainer).width(165).height(125);
    } else {
      button.add(new Image(skin.getDrawable("top"))).width(165).height(125);
    }
    button.row();
    
    Label label = new Label(appConfigItem.getDisplayName(), skin);
    label.setFontScale(2f);
    label.setAlignment(Align.bottom);  
    button.add(label).width(150).height(90).padTop(20);
    
    button.setName(appConfigItem.getName());
    button.addListener(appClickListener);   
    return button;
  }
  
  /**
   * Handle clicking an app button. This will start the Machine and run the selected app.
   */
  public ClickListener appClickListener = new ClickListener() {
    @Override
    public void clicked (InputEvent event, float x, float y) {
      String appName = event.getListenerActor().getName();
      if ((appName != null) && (!appName.equals(""))) {
        AppConfigItem appConfigItem = appConfigMap.get(appName);
        if (appConfigItem != null) {
          MachineScreen machineScreen = jvic.getMachineScreen();
          machineScreen.initMachine(appConfigItem);
          jvic.setScreen(machineScreen);
        }
      }
    }
  };
}
