package emu.jvic.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.WindowedMean;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Table.Debug;
import com.badlogic.gdx.utils.Array;

import emu.jvic.HomeScreen;

public class PagedScrollPane extends ScrollPane {

    private static final int CONTENT_SPACING = 50;
    
    private boolean wasPanDragFling = false;

    private float lastScrollX = 0;

    private float lastVisualScrollX = 0;
    
    private boolean wasStillMovingLastAct;
    
    private WindowedMean scrollXDeltaMean = new WindowedMean(5);

    private Table content;

    private int currentSelectionIndex;
    
    private HomeScreen homeScreen;
    
    public PagedScrollPane() {
        super(null);
        setup();
    }

    public PagedScrollPane(Skin skin) {
        super(null, skin);
        setup();
    }

    public PagedScrollPane(Skin skin, String styleName) {
        super(null, skin, styleName);
        setup();
    }

    public PagedScrollPane(Actor widget, ScrollPaneStyle style) {
        super(null, style);
        setup();
    }

    public void setHomeScreen(HomeScreen homeScreen) {
        this.homeScreen = homeScreen;
    }
    
    private void setup() {
        content = new Table();
        content.defaults().space(CONTENT_SPACING);
        super.setWidget(content);
        Button.debugCellColor = new Color(1, 1, 1, 1.0f);
    }
    
    public void addPages(Actor... pages) {
        for (Actor page : pages) {
            content.add(page).expandY().fillY();
        }
    }

    public void addPage(Actor page) {
        content.add(page).expandY().fillY();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (wasPanDragFling && !isPanning() && !isDragging() && !isFlinging()) {
            wasPanDragFling = false;
            scrollToPage();
            scrollXDeltaMean.clear();
        } else {
            if (isPanning() || isDragging() || isFlinging()) {
                wasPanDragFling = true;
                scrollXDeltaMean.addValue(getScrollX() - lastScrollX);
                lastScrollX = getScrollX();
            } else {
                if (lastVisualScrollX != getVisualScrollX()) {
                    wasStillMovingLastAct = true;
                    updateSelectionToBeOnCurrentPage();
                } else {
                    if (wasStillMovingLastAct) {
                        updateSelectionToBeOnCurrentPage();
                    }
                    wasStillMovingLastAct = false;
                }
                lastVisualScrollX = getVisualScrollX();
            }
        }
    }
    
    public void updateSelectionToBeOnCurrentPage() {
        int pageNumber = getCurrentPageNumber();
        if (pageNumber > 0) {
            // We only set the selection if it isn't already on the current page.
            int programsPerPage = getProgramsPerPage();
            int firstOnPage = programsPerPage * (pageNumber - 1);
            int lastOnPage = ((firstOnPage + programsPerPage) - 1);
            if ((currentSelectionIndex < firstOnPage) || (currentSelectionIndex > lastOnPage)) {
                int newSelectionIndex = getProgramsPerPage() * (pageNumber - 1);
                updateSelection(newSelectionIndex, false);
            }
        } else {
            // When on home screen, always set selection to first one.
            updateSelection(0, false);
        }
    }

    @Override
    public void setWidth(float width) {
        super.setWidth(width);
        if (content != null) {
            for (Cell cell : content.getCells()) {
                cell.width(width);
            }
            content.invalidate();
        }
    }

    public void setPageSpacing(float pageSpacing) {
        if (content != null) {
            content.defaults().space(pageSpacing);
            for (Cell cell : content.getCells()) {
                cell.space(pageSpacing);
            }
            content.invalidate();
        }
    }

    public void setLastScrollX(float lastScrollX) {
        this.lastScrollX = lastScrollX;
    }
    
    public void reset() {
        this.scrollXDeltaMean.clear();
        this.lastScrollX = 0;
        this.wasPanDragFling = false;
        setScrollX(0);
    }
    
    /**
     * Gets the number of programs on each page.
     * 
     * @return The number of programs on each page.
     */
    public int getProgramsPerPage() {
        int gamesPerPage = 0;
        if (content.getChildren().notEmpty()) {
            // Checks the second page, so as to ignore title page.
            Table secondPage = (Table)content.getChild(1);
            gamesPerPage = secondPage.getColumns() * secondPage.getRows();
        }
        return gamesPerPage;
    }
    
    /**
     * Gets the number of columns in each page.
     * 
     * @return The number of columns in each page.
     */
    public int getNumOfColumns() {
        int numOfColumns = 0;
        if (content.getChildren().notEmpty()) {
            Table secondPage = (Table)content.getChild(1);
            numOfColumns = secondPage.getColumns();
        }
        return numOfColumns;
    }
    
    /**
     * Gets the number of rows in each page.
     * 
     * @return The number of rows in each page.
     */
    public int getNumOfRows() {
        int numOfRows = 0;
        if (content.getChildren().notEmpty()) {
            Table secondPage = (Table)content.getChild(1);
            numOfRows = secondPage.getRows();
        }
        return numOfRows;
    }
    
    /**
     * Gets the total number of pages on this paged scroll pane.
     * 
     * @return The total number of pages.
     */
    public int getNumOfPages() {
        return content.getChildren().size;
    }
    
    /**
     * Gets the page number of the page currently being seen in this paged scroll pane.
     * 
     * @return The page number of the page currently being seen.
     */
    public int getCurrentPageNumber() {
        int pageNumber = 0;
        if (content.getChildren().notEmpty()) {
            int pageWidth = (int)(content.getChild(0).getWidth() + CONTENT_SPACING);
            pageNumber = Math.round(getScrollX() / pageWidth);
        }
        return pageNumber;
    }
    
    /**
     * Gets the Table representing the given page.
     * 
     * @param pageNum The number of the page to get the Table for.
     * 
     * @return The Table representing the specified page.
     */
    public Table getPage(int pageNum) {
        return ((Table)content.getChild(pageNum));
    }
    
    /**
     * Gets the total number of programs on this paged scroll pane.
     * 
     * @return The total number of programs.
     */
    public int getNumOfPrograms() {
        return homeScreen.getAppConfigMap().size();
    }
    
    /**
     * Gets the Button at the given program index.
     * 
     * @param programIndex The index to get the program Button for.
     * 
     * @return The program Button at the given index.
     */
    public Button getProgramButton(int programIndex) {
        int programsPerPage = getProgramsPerPage();
        int page = programIndex / programsPerPage;
        int programOnPageIndex = programIndex - (page * programsPerPage);
        Table pageTable = (Table)content.getChild(page + 1);
        
        // First page is the title page, so we protect against that.
        if (pageTable.getChildren().size > programOnPageIndex) {
            if (pageTable.getChild(programOnPageIndex) instanceof Button) {
                return (Button)pageTable.getChild(programOnPageIndex);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * Gets the currently selected program index.
     * 
     * @return The currently selected program index.
     */
    public int getCurrentSelectionIndex() {
        return currentSelectionIndex;
    }
    
    /**
     * Gets the program Button for the currently selected program.
     * 
     * @return The program Button for the currently selected program.
     */
    public Button getCurrentlySelectedProgramButton() {
        return getProgramButton(currentSelectionIndex);
    }
    
    /**
     * Attempts to update the selected program to the one identified by the given
     * program index, i.e. index into the list of program icons within this paged
     * scroll pane.
     * 
     * @param newSelectionIndex The index of the program icon to select.
     */
    public void updateSelection(int newSelectionIndex) {
        updateSelection(newSelectionIndex, true);
    }
    
    /**
     * Attempts to update the selected program to the one identified by the given
     * program index, i.e. index into the list of program icons within this paged
     * scroll pane.
     * 
     * @param newSelectionIndex The index of the program icon to select.
     * @param showPage Whether to scroll to show the page the program is on, or not.
     */
    public void updateSelection(int newSelectionIndex, boolean showPage) {
        int numberOfPrograms = getNumOfPrograms();
        
        // Bounds checks.
        if (newSelectionIndex < 0) {
            newSelectionIndex = 0;
        }
        if (newSelectionIndex > numberOfPrograms) {
            newSelectionIndex = numberOfPrograms;
        }
        
        if (newSelectionIndex != currentSelectionIndex) {
            // Remove highlight from previously selected program.
            updateSelectionHighlight(currentSelectionIndex, false);
            
            // Add highlight to newly selected program.
            updateSelectionHighlight(newSelectionIndex, true);
            currentSelectionIndex = newSelectionIndex;
            
            if (showPage) {
                // Move to the page that the program is on, if required. 
                showProgramPage(currentSelectionIndex);
            }
        } else {
            // In some scenarios, the currently selected one isn't highlighted, 
            // so we always apply the highlight, which may not change anything.
            updateSelectionHighlight(currentSelectionIndex, true);
        }
    }
    
    /**
     * Updates the highlight status of the program icon identified by the
     * given index. It uses the libGDX built in "debug" highlighted feature
     * as a simple way to highlight.
     * 
     * @param programIndex The index of the program to update the highlight for.
     * @param highlight Whether or not to highlight the icon.
     */
    public void updateSelectionHighlight(int programIndex, boolean highlight) {
        Button programButton = getProgramButton(programIndex);
        if (programButton != null) {
            if (!homeScreen.isMobile()) {
                programButton.debug(highlight? Debug.cell : Debug.none);
            }
        }
    }
    
    /**
     * Navigates to the next program.
     */
    public void nextProgram() {
        updateSelection(currentSelectionIndex + 1);
    }
    
    /**
     * Navigates to the previous program.
     */
    public void prevProgram() {
        updateSelection(currentSelectionIndex - 1);
    }
    
    /**
     * Navigates to the next row of programs.
     */
    public void nextProgramRow() {
        updateSelection(currentSelectionIndex + getNumOfColumns());
    }
    
    /**
     * Navigates to the previous row of programs.
     */
    public void prevProgramRow() {
        updateSelection(currentSelectionIndex - getNumOfColumns());
    }
    
    /**
     * Navigates to the next page of programs.
     */
    public void nextProgramPage() {
        updateSelection(currentSelectionIndex + getProgramsPerPage(), false);
    }
    
    /**
     * Navigates to the previous page of programs.
     */
    public void prevProgramPage() {
        updateSelection(currentSelectionIndex - getProgramsPerPage(), false);
    }
    
    /**
     * Gets the number of pixels gap between pages.
     * 
     * @return The number of pixels gap between pages.
     */
    public int getContentSpacing() {
        return CONTENT_SPACING;
    }
    
    /**
     * This method is used by the key navigation, i.e. when it has calculated a specific
     * program index to move to. The navigation keys are used to navigation +/- one
     * item at a time, or page up/down at a time, then after the new index has been
     * calculated, the PagedScrollPane moves to the new page, if required.
     * 
     * @param programIndex The index of the program to move to.
     */
    private void showProgramPage(int programIndex) {
        // Work out how far to move from far left to get to program's page.
        int programsPerPage = getProgramsPerPage();
        float pageWidth = ViewportManager.getInstance().isPortrait()? 
                1080.0f + CONTENT_SPACING : 1920.0f + CONTENT_SPACING;
        float newScrollX = pageWidth * (programIndex / programsPerPage) + pageWidth;
        
        setScrollX(newScrollX);
        setLastScrollX(newScrollX);
    }
    
    /**
     * This method is used by the fling mechanism and not by key navigation.
     */
    private void scrollToPage() {
        final float width = getWidth();
        final float scrollX = getScrollX();
        final float maxX = getMaxX();

        if (scrollX >= maxX || scrollX <= 0)
            return;

        Array<Actor> pages = content.getChildren();
        float pageX = 0;
        float pageWidth = 0;

        float scrollXDir = scrollXDeltaMean.getMean();
        if (scrollXDir == 0) {
            scrollXDir = scrollXDeltaMean.getLatest();
        }

        for (Actor a : pages) {
            pageX = a.getX();
            pageWidth = a.getWidth();
            if (scrollXDir > 0) {
                if (scrollX < (pageX + pageWidth * 0.1)) {
                    break;
                }
            } else if (scrollXDir < 0) {
                if (scrollX < (pageX + pageWidth * 0.9)) {
                    break;
                }
            } else {
                if (scrollX < (pageX + pageWidth * 0.5)) {
                    break;
                }
            }
        }

        float newScrollX = MathUtils.clamp(pageX - (width - pageWidth) / 2, 0, maxX);
        setScrollX(newScrollX);
        this.lastScrollX = newScrollX;
    }
}
