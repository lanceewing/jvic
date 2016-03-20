package emu.jvic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
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

import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.PagedScrollPane;
import emu.jvic.ui.ViewportManager;

/**
 * The Home screen of the JVic emulator, i.e. the one that shows all of the 
 * available boot options and programs to load. Reminiscent of the Android 
 * home screen.
 * 
 * @author Lance Ewing
 */
public class HomeScreen implements Screen  {

  private Skin skin;
  private Stage stage;
  private Table container;
  private ViewportManager viewportManager;
  
  /**
   * Constructor for HomeScreen.
   * 
   * @param confirmHandler
   */
  public HomeScreen(ConfirmHandler confirmHandler) {
    viewportManager = ViewportManager.getInstance();
    
    stage = new Stage(viewportManager.getCurrentViewport());
    
    skin = new Skin(Gdx.files.internal("data/uiskin.json"));
    skin.add("top", skin.newDrawable("default-round", Color.RED), Drawable.class);
    skin.add("star-filled", skin.newDrawable("white", Color.YELLOW), Drawable.class); 
    skin.add("star-unfilled", skin.newDrawable("white", Color.GRAY), Drawable.class);
    
    container = new Table();
    stage.addActor(container);
    container.setFillParent(true);

    PagedScrollPane scroll = new PagedScrollPane();
    scroll.setFlingTime(0.1f);
    scroll.setPageSpacing(25);
    int c = 1;
    for (int l = 0; l < 10; l++) {
      Table levels = new Table().pad(50);
      levels.debugCell();
      //levels.debugTable();
      levels.defaults().pad(100, 40, 100, 40);
      for (int y = 0; y < 5; y++) {
        levels.row();
        for (int x = 0; x < 4; x++) {
          levels.add(getLevelButton(c++)).expand().fill();
        }
      }
      scroll.addPage(levels);
    }
    container.add(scroll).expand().fill();
  }
  
  @Override
  public void show() {
    Gdx.input.setInputProcessor(stage);
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
   * Creates a button to represent the level
   * 
   * @param level
   * @return The button to use for the level
   */
  public Button getLevelButton(int level) {
    Button button = new Button(skin);
    ButtonStyle style = button.getStyle();
    style.up =  style.down = null;
    
    // Create the label to show the level number
    Label label = new Label(Integer.toString(level), skin);
    label.setFontScale(2f);
    label.setAlignment(Align.center);   
    
    // Stack the image and the label at the top of our button
    button.stack(new Image(skin.getDrawable("top")), label).expand().fill();

    // Randomize the number of stars earned for demonstration purposes
    int stars = MathUtils.random(0, +3);
    Table starTable = new Table();
    starTable.defaults().pad(5);
    //if (stars >= 0) {
      for (int star = 0; star < 3; star++) {
        if (stars > star) {
          starTable.add(new Image(skin.getDrawable("star-filled"))).width(45).height(20);
        } else {
          starTable.add(new Image(skin.getDrawable("star-unfilled"))).width(45).height(20);
        }
      }     
    //}
    
    button.row();
    button.add(starTable).height(30);
    
    button.setName("Level" + Integer.toString(level));
    button.addListener(levelClickListener);   
    return button;
  }
  
  /**
   * Handle the click - in real life, we'd go to the level
   */
  public ClickListener levelClickListener = new ClickListener() {
    @Override
    public void clicked (InputEvent event, float x, float y) {
      System.out.println("Click: " + event.getListenerActor().getName());
    }
  };
}
