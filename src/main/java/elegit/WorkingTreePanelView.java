package elegit;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Callback;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.util.*;

/**
 *
 * WorkingTreePanelView displays the current working tree's directory and
 * lets the user mark checkboxes on files to commit. The 'commit action' to
 * be performed on a checkboxed file is determined by that file's status:
 * untracked/new, modified, or deleted.
 *
 */
public class WorkingTreePanelView extends FileStructurePanelView{

    public BooleanProperty isAnyFileSelectedProperty;

    public List<TreeItem<RepoFile>> displayedFiles;

    private CheckBoxTreeItem<RepoFile> checkBox;

    public WorkingTreePanelView() {
        this.init();
    }

    @Override
    public void init(){
        this.displayedFiles = new LinkedList<>();
        isAnyFileSelectedProperty = new SimpleBooleanProperty(false);

        // used to disable/enable add and remove buttons
        isAnyFileSelectedProperty.addListener(((observable, oldValue, newValue) -> SessionController.anythingChecked.set(newValue)));

        super.init();
    }

    /**
     * @return the cell from CheckBoxTreeCell's implementation of a TreeView factory, with
     * an added context menu for the given RepoFile
     */
    @Override
    protected Callback<TreeView<RepoFile>, TreeCell<RepoFile>> getTreeCellFactory() {
        return arg -> {
            TreeCell<RepoFile> cell = CheckBoxTreeCell.<RepoFile>forTreeView().call(arg);
            cell.setOnContextMenuRequested(event -> {
                if(cell.getTreeItem()!= null) cell.getTreeItem().getValue().showContextMenu(cell, event.getScreenX(), event.getScreenY());
            });
            return cell;
        };
    }

    @Override
    protected TreeItem<RepoFile> getRootTreeItem(DirectoryRepoFile rootDirectory) {
        TreeItem<RepoFile> item = new CheckBoxTreeItem<>(rootDirectory);
        initCheckBox();
        item.getChildren().add(checkBox);
        return item;
    }

    private void initCheckBox() {
        Text txt = new Text("select all");
        txt.setFont(new Font(10));
        checkBox = new CheckBoxTreeItem<>(null, txt);
        checkBox.selectedProperty().addListener(((observable, oldValue, newValue) -> {
            if(newValue) {
                setAllFilesSelected(true);
                txt.setText("deselect all");
            }else {
                setAllFilesSelected(false);
                txt.setText("select all");
            }
        }));
    }


    /**
     * Adds all tracked files in the repository with an updated status and displays them
     * all as top-level items.
     * @param repoFiles the files to add to the tree
     * @param root the root of the tree
     */
    @Override
    protected void addTreeItemsToRoot(List<RepoFile> repoFiles, TreeItem<RepoFile> root) {
        displayedFiles = new LinkedList<>();

        //re-adds the checkbox if it had been removed
        if(root.getChildren().size() == 0) {
            root.getChildren().add(checkBox);
        }

        // Helper to construct 'isAnyFileSelectedProperty'
        BooleanProperty isSelectedPropertyHelper = new SimpleBooleanProperty(false);

        // Track all current children of root to make sure they should still be displayed
        // starts at index 1 because the select all checkbox is at index 0
        Map<TreeItem<RepoFile>, Boolean> shouldKeepChild = new HashMap<>();
        for(int i = 1; i < root.getChildren().size(); i++){
            shouldKeepChild.put(root.getChildren().get(i), false);
        }

        // Loop over every file to be shown
        for(RepoFile repoFile : repoFiles) {
            CheckBoxTreeItem<RepoFile> newItem = new CheckBoxTreeItem<>(repoFile, repoFile.diffButton);

            BooleanProperty oldHelper = isSelectedPropertyHelper;
            isSelectedPropertyHelper = new SimpleBooleanProperty();

            // Check if the file is already being displayed
            // starts at index 1 because the select all checbox is at index 0
            boolean foundMatchingItem = false;
            for(int i = 1; i < root.getChildren().size(); i++){
                CheckBoxTreeItem<RepoFile> oldItem = (CheckBoxTreeItem<RepoFile>) root.getChildren().get(i);

                if(oldItem.getValue().equals(repoFile)){
                    // The given file is already present, no additional processing necessary
                    isSelectedPropertyHelper.bind(oldHelper.or(oldItem.selectedProperty()));
                    displayedFiles.add(oldItem);
                    foundMatchingItem = true;
                    shouldKeepChild.put(oldItem, true);
                    break;
                }else if(oldItem.getValue().getFilePath().equals(repoFile.getFilePath())){
                    // The file was being displayed, but its status has changed. Replace the old with the new
                    newItem.setSelected(oldItem.isSelected());
                    root.getChildren().set(i, newItem);
                    isSelectedPropertyHelper.bind(oldHelper.or(newItem.selectedProperty()));
                    displayedFiles.add(newItem);
                    foundMatchingItem = true;
                    shouldKeepChild.put(newItem, true);
                    break;
                }
            }

            // The file wasn't being displayed, so add it
            if(!foundMatchingItem){
                root.getChildren().add(newItem);
                isSelectedPropertyHelper.bind(oldHelper.or(newItem.selectedProperty()));

                displayedFiles.add(newItem);

                shouldKeepChild.put(newItem, true);
            }
        }

        // Remove all elements that shouldn't be displayed
        for(TreeItem item : shouldKeepChild.keySet()){
            if(!shouldKeepChild.get(item)){
                root.getChildren().remove(item);
            }
        }

        //hides the checkbox if there are no files shown
        if(root.getChildren().size() < 2) {
            root.getChildren().remove(checkBox);
        }

        isAnyFileSelectedProperty.bind(isSelectedPropertyHelper);
    }

    /**
     * @return all tracked files with an updated status
     * @throws GitAPIException
     */
    @Override
    public List<RepoFile> getFilesToDisplay() throws GitAPIException{
        return sessionModel.getAllChangedRepoFiles();
    }

    /**
     * Checks through all the files and finds all whose checkbox is checked.
     *
     * @return an array of RepoFiles whose CheckBoxTreeItem cells are checked.
     */
    public ArrayList<RepoFile> getCheckedFilesInDirectory() {
        ArrayList<RepoFile> checkedFiles = new ArrayList<>();
        for (TreeItem fileLeaf : this.displayedFiles) {
            CheckBoxTreeItem checkBoxFile = (CheckBoxTreeItem) fileLeaf;
            if (checkBoxFile.isSelected())
                checkedFiles.add((RepoFile)fileLeaf.getValue());
        }
        return checkedFiles;
    }

    /**
     * Sets all displayed items to have the given selected status
     * @param selected true to check every box, false to uncheck every box
     */
    public void setAllFilesSelected(boolean selected) {
        for (TreeItem fileLeaf : displayedFiles) {
            CheckBoxTreeItem checkBoxFile = (CheckBoxTreeItem) fileLeaf;
            checkBoxFile.setSelected(selected);
        }
    }

    /**
     * @return true if any file is checked, else false
     */
    public boolean isAnyFileSelected(){
        return isAnyFileSelectedProperty.get();
    }

    /**
     * @return true if any file has been staged, else false
     */
    public boolean isAnyFileStaged() {
        for (TreeItem<RepoFile> treeItem: displayedFiles) {
            if (treeItem.getValue() instanceof StagedAndModifiedRepoFile || treeItem.getValue() instanceof StagedRepoFile)
                return true;
        }
        return false;
    }

    /**
     * @return true if any file that is selected is a staged file
     */
    public boolean isAnyFileStagedSelected() {
        for (RepoFile treeItem : getCheckedFilesInDirectory()) {
            if (treeItem instanceof StagedRepoFile)
                return true;
        }
        return false;
    }
}
