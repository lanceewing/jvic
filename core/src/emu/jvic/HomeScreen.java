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
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Json;

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
  private Stage stage;
  private Table container;
  private ViewportManager viewportManager;
  private Map<String, AppConfigItem> appConfigMap;
  
  /**
   * Invoked by JVic whenever it would like the user to confirm an action.
   */
  private ConfirmHandler confirmHandler;
  
  /**
   * The InputProcessor for the Home screen. This is an InputMultiplexor, which includes 
   * both the Stage and the HomeScreen.
   */
  private InputMultiplexer inputProcessor;
  
  /**
   * Constructor for HomeScreen.
   * 
   * @param jvic The JVicGdx instance.
   * @param confirmHandler
   */
  public HomeScreen(JVicGdx jvic, ConfirmHandler confirmHandler) {
    this.jvic = jvic;
    this.confirmHandler = confirmHandler;
    
    viewportManager = ViewportManager.getInstance();
    
    stage = new Stage(viewportManager.getCurrentViewport());
    
    Json json = new Json();
    AppConfig appConfig = json.fromJson(AppConfig.class, Gdx.files.internal("data/programs.json"));
    appConfigMap = new HashMap<String, AppConfigItem>();
    
    skin = new Skin(Gdx.files.internal("data/uiskin.json"));
    skin.add("top", skin.newDrawable("default-round", Color.RED), Drawable.class);
    skin.add("star-filled", skin.newDrawable("white", Color.YELLOW), Drawable.class); 
    skin.add("star-unfilled", skin.newDrawable("white", Color.GRAY), Drawable.class);
    
    container = new Table();
    stage.addActor(container);
    container.setFillParent(true);

    PagedScrollPane scroll = new PagedScrollPane();
    scroll.setFlingTime(0.01f);
    scroll.setPageSpacing(25);
    
    int pageItemCount = 0;
    Table currentPage = new Table().pad(50);
    currentPage.defaults().pad(55, 40, 55, 40);
    
    for (AppConfigItem appConfigItem : appConfig.getApps()) {
      appConfigMap.put(appConfigItem.getName(), appConfigItem);
      
      // Every 20 apps, add a new page.
      if (pageItemCount == 20) {
        scroll.addPage(currentPage);
        pageItemCount = 0;
        currentPage = new Table().pad(50);
        currentPage.defaults().pad(55, 40, 55, 40);
      }
      
      // Every 4 apps, add a new row to the current page.
      if ((pageItemCount % 4) == 0) {
        currentPage.row();
      }
      
      currentPage.add(buildAppButton(appConfigItem)).expand().fill();
      
      pageItemCount++;
    }
    
    // Add the last page of apps.
    if (pageItemCount <= 20) {
      AppConfigItem appConfigItem = new AppConfigItem();
      appConfigMap.put("", appConfigItem);
      for (int i=pageItemCount; i<20; i++) {
        if ((i % 4) == 0) {
          currentPage.row();
        }
        currentPage.add(buildAppButton(appConfigItem)).expand().fill();
      }
      scroll.addPage(currentPage);
    }

    container.add(scroll).expand().fill();
    
    // The stage handles most of the input, but we need to handle the BACK button separately.
    inputProcessor = new InputMultiplexer();
    inputProcessor.addProcessor(stage);
    inputProcessor.addProcessor(this);
  }
  
  @Override
  public void show() {
    Gdx.input.setInputProcessor(inputProcessor);
  }

  @Override
  public void render(float delta) {
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    stage.act(delta);
    stage.draw();
  }

  @Override
  public void resize(int width, int height) {
    stage.getViewport().update(width, height, false);
    viewportManager.update(width, height);
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
    stage.dispose();
    skin.dispose();
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
    
    // TODO: Load the app icon image at this point.
    button.add(new Image(skin.getDrawable("top"))).expand().fill();
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
