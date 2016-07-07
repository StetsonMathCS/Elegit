package elegit;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Created by connellyj on 7/7/16.
 *
 * Class that initializes a given pop up window
 */
public class PopUpWindows {

    static final Logger logger = LogManager.getLogger();

    /**
     * Informs the user that they are about to commit a conflicting file
     * @return String user's response to the dialog
     */
    public static String showCommittingConflictingFileAlert() {
        String resultType;

        Alert alert = new Alert(Alert.AlertType.WARNING);

        ButtonType resolveButton = new ButtonType("Open Editor");
        ButtonType addButton = new ButtonType("Commit");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType helpButton = new ButtonType("Help", ButtonBar.ButtonData.HELP);

        alert.getButtonTypes().setAll(helpButton, resolveButton, addButton, cancelButton);

        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(450, 200);

        alert.setTitle("Warning: conflicting file");
        alert.setHeaderText("You're adding a conflicting file to the commit");
        alert.setContentText("You can open an editor to resolve the conflicts, or commit the changes anyways. What do you want to do?");

        ImageView img = new ImageView(new javafx.scene.image.Image("/elegit/conflict.png"));
        img.setFitHeight(40);
        img.setFitWidth(80);
        img.setPreserveRatio(true);
        alert.setGraphic(img);

        Optional<ButtonType> result = alert.showAndWait();

        if(result.get() == resolveButton){
            logger.info("Chose to resolve conflicts");
            resultType = "resolve";
        }else if(result.get() == addButton){
            logger.info("Chose to add file");
            resultType = "add";
        }else if(result.get() == helpButton) {
            logger.info("Chose to get help");
            resultType = "help";
        } else{
            // User cancelled the dialog
            logger.info("Cancelled dialog");
            resultType = "cancel";
        }

        return resultType;
    }

    /**
     * Shows a window with instructions on how to fix a conflict
     */
    public static void showConflictingHelpAlert() {
        Platform.runLater(() -> {
            Alert window = new Alert(Alert.AlertType.INFORMATION);
            window.setResizable(true);
            window.getDialogPane().setPrefSize(550, 350);
            window.setTitle("How to fix conflicting files");
            window.setHeaderText("How to fix conflicting files");
            window.setContentText("1. First, open up the file that is marked as conflicting.\n" +
                    "2. In the file, you should see something like this:\n\n" +
                    "\t<<<<<< <branch_name>\n" +
                    "\tChanges being made on the branch that is being merged into.\n" +
                    "\tIn most cases, this is the branch that you currently have checked out (i.e. HEAD).\n" +
                    "\t=======\n" +
                    "\tChanges made on the branch that is being merged in.\n" +
                    "\t>>>>>>> <branch name>\n\n" +
                    "3. Delete the contents you don't want to keep after the merge\n" +
                    "4. Remove the markers (<<<<<<<, =======, >>>>>>>) git put in the file\n" +
                    "5. Done! You can now safely commit the file");
            window.showAndWait();
        });
    }

    /**
     * Shows a window with some info about git reset
     */
    public static void showResetHelpAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.getDialogPane().setPrefSize(300, 300);
            alert.setTitle("Reset Help");
            alert.setHeaderText("What is reset?");
            ImageView img = new ImageView(new Image("/elegit/undo.png"));
            img.setFitHeight(60);
            img.setFitWidth(60);
            alert.setGraphic(img);
            alert.setContentText("Move the current branch tip backward to the selected commit, " +
                    "reset the staging area to match, " +
                    "but leave the working directory alone. " +
                    "All changes made since the selected commit will reside in the working directory.");
            alert.showAndWait();
        });
    }

    /**
     * Show a window with info about git revert
     */
    public static void showRevertHelpAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.getDialogPane().setPrefSize(300, 300);
            alert.setTitle("Revert Help");
            alert.setHeaderText("What is revert?");
            ImageView img = new ImageView(new Image("/elegit/undo.png"));
            img.setFitHeight(60);
            img.setFitWidth(60);
            alert.setGraphic(img);
            alert.setContentText("The git revert command undoes a committed snapshot. " +
                    "But, instead of removing the commit from the project history, " +
                    "it figures out how to undo the changes introduced by the commit and appends a new commit with the resulting content. " +
                    "This prevents Git from losing history, " +
                    "which is important for the integrity of your revision history and for reliable collaboration.");
            alert.showAndWait();
        });
    }

    /**
     * Informs the user that they are committing a previously conflicting file
     * @return String result from user input
     */
    public static String showCommittingConflictingThenModifiedFileAlert() {
        String resultType;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        ButtonType commitButton = new ButtonType("Commit");
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(commitButton, buttonTypeCancel);

        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(300, 200);

        alert.setTitle("Adding previously conflicting file");
        alert.setHeaderText("You are adding a conflicting file that was recently modified to the commit");
        alert.setContentText("If the file is what you want it to be, you should commit. Otherwise, modify the file accordingly.");

        Optional<ButtonType> result = alert.showAndWait();

        if(result.get() == commitButton){
            logger.info("Chose to commit");
            resultType = "commit";
        }else{
            // User cancelled the dialog
            logger.info("Cancelled dialog");
            resultType = "cancel";
        }

        return resultType;
    }

    /**
     * Informs the user that they are tracking ignored files
     * @param trackedIgnoredFiles collections of files being ignored
     */
    public static void showTrackingIgnoredFilesWarning(Collection<String> trackedIgnoredFiles) {
        Platform.runLater(() -> {
            if(trackedIgnoredFiles.size() > 0){
                String fileStrings = "";
                for(String s : trackedIgnoredFiles){
                    fileStrings += "\n"+s;
                }
                Alert alert = new Alert(Alert.AlertType.WARNING, "The following files are being tracked by Git, " +
                        "but also match an ignore pattern. If you want to ignore these files, remove them from Git.\n"+fileStrings);
                alert.showAndWait();
            }
        });
    }

    /**
     * Informs the user that there are conflicting files so they can't checkout a different branch
     * @param conflictingPaths conflicting files
     */
    public static void showCheckoutConflictsAlert(List<String> conflictingPaths) {
        logger.warn("Checkout conflicts warning");
        String conflictList = "";
        for(String pathName : conflictingPaths){
            conflictList += "\n" + pathName;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Conflicting files");
        alert.setHeaderText("Can't checkout that branch");
        alert.setContentText("You can't switch to that branch because of the following conflicting files between that branch and your current branch: "
                + conflictList);

        alert.showAndWait();
    }

    /**
     * Informs the user that there were conflicts
     * @param conflictingPaths conflicting files
     */
    public static void showtMergeConflictsAlert(List<String> conflictingPaths) {
        logger.warn("Merge conflicts warning");
        String conflictList = "";
        for(String pathName : conflictingPaths){
            conflictList += "\n" + pathName;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Conflicting files");
        alert.setHeaderText("Can't complete merge");
        alert.setContentText("There were conflicts in the following files: "
                + conflictList);

        alert.showAndWait();
    }
}