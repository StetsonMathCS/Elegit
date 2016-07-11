package elegit;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.NotificationPane;
import org.controlsfx.control.action.Action;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

/**
 *
 * A controller for the BranchManager view that holds all a repository's
 * branches (in the form of BranchHelpers) and manages branch creation,
 * deletion, and tracking of remotes.
 *
 */
public class BranchCheckoutController {

    public Text branchOffFromText;
    public TextField newBranchNameField;
    public ListView<RemoteBranchHelper> remoteListView;
    public ListView<LocalBranchHelper> localListView;
    private Repository repo;
    private RepoHelper repoHelper;
    private BranchModel branchModel;
    @FXML
    private static NotificationPane notificationPane;
    @FXML
    private Button trackRemoteBranchButton;
    @FXML
    private Button checkoutLocalBranchButton;

    private SessionModel sessionModel;
    private LocalCommitTreeModel localCommitTreeModel;
    private RemoteCommitTreeModel remoteCommitTreeModel;
    private Stage stage;

    static final Logger logger = LogManager.getLogger();

    public void initialize() throws Exception {

        logger.info("Started up branch manager");

        this.sessionModel = SessionModel.getSessionModel();
        this.repoHelper = this.sessionModel.getCurrentRepoHelper();
        this.repo = this.repoHelper.getRepo();
        this.branchModel = repoHelper.getBranchModel();
        for (CommitTreeModel commitTreeModel : CommitTreeController.allCommitTreeModels) {
            if (commitTreeModel.getViewName().equals(LocalCommitTreeModel
                    .LOCAL_TREE_VIEW_NAME)) {
                this.localCommitTreeModel = (LocalCommitTreeModel)commitTreeModel;
            } else if (commitTreeModel.getViewName().equals(RemoteCommitTreeModel
                    .REMOTE_TREE_VIEW_NAME)) {
                this.remoteCommitTreeModel = (RemoteCommitTreeModel)commitTreeModel;
            }
        }
        this.remoteListView.setItems(FXCollections.observableArrayList(branchModel.getRemoteBranchesTyped()));
        this.localListView.setItems(FXCollections.observableArrayList(branchModel.getLocalBranchesTyped()));

        // Local list view can select multiple (for merges):
        this.localListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        this.remoteListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        this.setIcons();
        this.updateButtons();
    }

    /**
     * A helper method that sets the icons and colors for buttons
     */
    private void setIcons() {
        Text cloudDownIcon = GlyphsDude.createIcon(FontAwesomeIcon.CLOUD_DOWNLOAD);
        cloudDownIcon.setFill(Color.WHITE);
        this.trackRemoteBranchButton.setGraphic(cloudDownIcon);
    }

    /**
     * Shows the branch manager
     * @param pane NotificationPane
     */
    public void showStage(NotificationPane pane) {
        stage = new Stage();
        stage.setTitle("Branch Manager");
        stage.setScene(new Scene(pane, 550, 450));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setOnCloseRequest(event -> logger.info("Closed branch manager window"));
        stage.show();
    }

    /**
     * Closes the branch manager
     */
    public void closeWindow() {
        stage.close();
    }

    /**
     * Handles a mouse click on the remote list view
     */
    public void handleRemoteListViewMouseClick() {
        if (!localListView.getSelectionModel().isEmpty()) {
            localListView.getSelectionModel().clearSelection();
        }
        try {
            this.updateButtons();
        } catch (IOException e1) {
            logger.error("Branch manager remote list view mouse click error");
            logger.debug(e1.getStackTrace());
            e1.printStackTrace();
        }
    }

    /**
     * Handles a mouse click on the local list view
     */
    public void handleLocalListViewMouseClick() {
        if (!remoteListView.getSelectionModel().isEmpty()) {
            remoteListView.getSelectionModel().clearSelection();
        }
        try {
            this.updateButtons();
        } catch (IOException e1) {
            logger.error("Branch manager local list view mouse click error");
            logger.debug(e1.getStackTrace());
            e1.printStackTrace();
        }
    }

    public void onNewBranchButton() {
        try {
            logger.info("New branch button clicked");
            LocalBranchHelper newLocalBranch = this.createNewLocalBranch(this.newBranchNameField.getText());
            this.localListView.getItems().add(newLocalBranch);
            this.newBranchNameField.clear();
        } catch (InvalidRefNameException e1) {
            logger.warn("Invalid branch name warning");
            this.showInvalidBranchNameNotification();
        } catch (RefNotFoundException e1) {
            // When a repo has no commits, you can't create branches because there
            //  are no commits to point to. This error gets raised when git can't find
            //  HEAD.
            logger.warn("Can't create branch without a commit in the repo warning");
            this.showNoCommitsYetNotification();
        } catch (GitAPIException e1) {
            logger.warn("Git error");
            logger.debug(e1.getStackTrace());
            this.showGenericGitErrorNotification();
            e1.printStackTrace();
        } catch (IOException e1) {
            logger.warn("Unspecified IOException");
            logger.debug(e1.getStackTrace());
            this.showGenericErrorNotification();
            e1.printStackTrace();
        }
    }

    /**
     * Updates the track remote, merge, and delete local buttons'
     * text and/or disabled/enabled status.
     *
     * @throws IOException
     */
    private void updateButtons() throws IOException {

        // Update delete button
        if (this.localListView.getSelectionModel().getSelectedIndices().size() > 0) {
            this.checkoutLocalBranchButton.setDisable(false);
            // But keep trackRemoteBranchButton disabled
            this.trackRemoteBranchButton.setDisable(true);
        }

        // Update track button
        if (this.remoteListView.getSelectionModel().getSelectedIndices().size() > 0) {
            this.trackRemoteBranchButton.setDisable(false);
            // But keep the other buttons disabled
            this.checkoutLocalBranchButton.setDisable(true);
        }
    }

    /**
     * Tracks the selected branch (in the remoteListView) locally.
     *
     * @throws GitAPIException
     * @throws IOException
     */
    public void trackSelectedBranchLocally() throws GitAPIException, IOException {
        logger.info("Track remote branch locally button clicked");
        RemoteBranchHelper selectedRemoteBranch = this.remoteListView.getSelectionModel().getSelectedItem();
        try {
            if (selectedRemoteBranch != null) {
                LocalBranchHelper tracker = this.branchModel.trackRemoteBranch(selectedRemoteBranch);
                this.localListView.getItems().add(tracker);
                CommitTreeController.setBranchHeads(this.remoteCommitTreeModel, this.repoHelper);
                CommitTreeController.setBranchHeads(this.localCommitTreeModel, this.repoHelper);
            }
        } catch (RefAlreadyExistsException e) {
            logger.warn("Branch already exists locally warning");
            this.showRefAlreadyExistsNotification();
        }
    }

    /**
     * Deletes the selected branches (in the localListView) through git.
     */
    public void deleteSelectedLocalBranches() throws IOException {
        logger.info("Delete branches button clicked");

        for (LocalBranchHelper selectedBranch : this.localListView.getSelectionModel().getSelectedItems()) {
            try {
                if (selectedBranch != null) {
                    // Local delete:
                    this.branchModel.deleteLocalBranch(selectedBranch);
                    this.localListView.getItems().remove(selectedBranch);

                    // Reset the branch heads
                    CommitTreeController.setBranchHeads(this.localCommitTreeModel, this.repoHelper);
                    CommitTreeController.setBranchHeads(this.remoteCommitTreeModel, this.repoHelper);
                }
            } catch (NotMergedException e) {
                logger.warn("Can't delete branch because not merged warning");
                this.showNotMergedNotification(selectedBranch);
            } catch (CannotDeleteCurrentBranchException e) {
                logger.warn("Can't delete current branch warning");
                this.showCannotDeleteBranchNotification(selectedBranch);
            } catch (GitAPIException e) {
                logger.warn("Git error");
                this.showGenericGitErrorNotificationWithBranch(selectedBranch);
            }
        }
        // TODO: add optional delete from remote, too.
        // see http://stackoverflow.com/questions/11892766/how-to-remove-remote-branch-with-jgit
    }

    /**
     * Creates a new local branch using git.
     *
     * @param branchName the name of the new branch.
     * @return the new local branch's LocalBranchHelper.
     * @throws GitAPIException
     * @throws IOException
     */
    private LocalBranchHelper createNewLocalBranch(String branchName) throws GitAPIException, IOException {
        return this.branchModel.createNewLocalBranch(branchName);
    }

    /**
     * Deletes a given local branch through git, forcefully.
     */
    private void forceDeleteLocalBranch(LocalBranchHelper branchToDelete) {
        logger.info("Deleting local branch");

        try {
            if (branchToDelete != null) {
                // Local delete:
                this.branchModel.forceDeleteLocalBranch(branchToDelete);
                // Update local list view
                this.localListView.getItems().remove(branchToDelete);

                // Reset the branch heads
                CommitTreeController.setBranchHeads(this.localCommitTreeModel, this.repoHelper);
                CommitTreeController.setBranchHeads(this.remoteCommitTreeModel, this.repoHelper);
            }
        } catch (CannotDeleteCurrentBranchException e) {
            logger.warn("Can't delete current branch warning");
            this.showCannotDeleteBranchNotification(branchToDelete);
        } catch (GitAPIException e) {
            logger.warn("Git error");
            this.showGenericGitErrorNotificationWithBranch(branchToDelete);
            e.printStackTrace();
        }
    }

    /**
     * Performs a git merge between the currently checked out branch and
     * the selected local branch.
     *
     * @throws IOException
     * @throws GitAPIException
     */
    public void mergeSelectedBranchWithCurrent() throws IOException, GitAPIException {
        logger.info("Merging selected branch with current");
        // Get the branch to merge with
        LocalBranchHelper selectedBranch = this.localListView.getSelectionModel().getSelectedItem();

        // Get the merge result from the branch merge
        MergeResult mergeResult= this.branchModel.mergeWithBranch(selectedBranch);

        if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)){
            this.showConflictsNotification();
            ConflictingFileWatcher.watchConflictingFiles(sessionModel.getCurrentRepoHelper());

        } else if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE)) {
            this.showUpToDateNotification();

        } else if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.FAILED)) {
            this.showFailedMergeNotification();

        } else if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.MERGED)
                || mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.MERGED_NOT_COMMITTED)) {
            this.showMergeSuccessNotification();

        } else if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.FAST_FORWARD)) {
            this.showFastForwardMergeNotification();

        } else {
            System.out.println(mergeResult.getMergeStatus());
            // todo: handle all cases (maybe combine some)
        }
    }

    /**
     * Swaps the branches to be merged.
     *
     * For example, this action will change
     * `Merge MASTER into DEVELOP` into `Merge DEVELOP into MASTER.`
     *
     * @throws GitAPIException
     * @throws IOException
     */
    public void swapMergeBranches() throws GitAPIException, IOException {
        LocalBranchHelper selectedBranch = this.localListView.getSelectionModel().getSelectedItem();
        LocalBranchHelper checkedOutBranch = (LocalBranchHelper) this.branchModel.getCurrentBranch();

        selectedBranch.checkoutBranch();
        this.localListView.getSelectionModel().select(checkedOutBranch);
        this.updateButtons();

        this.showBranchSwapNotification(selectedBranch.getBranchName());
    }

    public static void checkoutBranch(LocalBranchHelper selectedBranch, SessionModel theSessionModel) {
        if(selectedBranch == null) return;
        Thread th = new Thread(new Task<Void>(){
            @Override
            protected Void call() {

                try{
                    // This is an edge case for new local repos.
                    //
                    // When a repo is first initialized,the `master` branch is checked-out,
                    //  but it is "unborn" -- it doesn't exist yet in the `refs/heads` folder
                    //  until there are commits.
                    //
                    // (see http://stackoverflow.com/a/21255920/5054197)
                    //
                    // So, check that there are refs in the refs folder (if there aren't, do nothing):
                    String gitDirString = theSessionModel.getCurrentRepo().getDirectory().toString();
                    Path refsHeadsFolder = Paths.get(gitDirString + "/refs/heads");
                    DirectoryStream<Path> pathStream = Files.newDirectoryStream(refsHeadsFolder);
                    Iterator<Path> pathStreamIterator = pathStream.iterator();

                    if (pathStreamIterator.hasNext()){ // => There ARE branch refs in the folder
                        selectedBranch.checkoutBranch();
                        CommitTreeController.focusCommitInGraph(selectedBranch.getHead());
                    }
                }catch(CheckoutConflictException e){
                    showCheckoutConflictsNotification(e.getConflictingPaths());
                }catch(GitAPIException | IOException e){
                    showGenericErrorNotification();
                    e.printStackTrace();
                }
                return null;
            }
        });
        th.setDaemon(true);
        th.setName("Branch Checkout");
        th.start();
    }

    public void handleCheckoutButton() {
        closeWindow();
        checkoutBranch(localListView.getSelectionModel().getSelectedItem(), sessionModel);
    }

    /// BEGIN: ERROR NOTIFICATIONS:

    private void showBranchSwapNotification(String newlyCheckedOutBranchName) {
        logger.info("Checked out a branch notification");
        notificationPane.setText(String.format("%s is now checked out.", newlyCheckedOutBranchName));

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showFastForwardMergeNotification() {
        logger.info("Fast forward merge complete notification");
        notificationPane.setText("Fast-forward merge completed (HEAD was updated).");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showMergeSuccessNotification() {
        logger.info("Merge completed notification");
        notificationPane.setText("Merge completed.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showFailedMergeNotification() {
        logger.warn("Merge failed notification");
        notificationPane.setText("The merge failed.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showUpToDateNotification() {
        logger.warn("No merge necessary notification");
        notificationPane.setText("No merge necessary. Those two branches are already up-to-date.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showGenericGitErrorNotificationWithBranch(LocalBranchHelper branch) {
        logger.warn("Git error on branch notification");
        notificationPane.setText(String.format("Sorry, there was a git error on branch %s.", branch.getBranchName()));

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showGenericGitErrorNotification() {
        logger.warn("Git error notification");
        notificationPane.setText("Sorry, there was a git error.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private static void showGenericErrorNotification() {
        logger.warn("Generic error notification");
        notificationPane.setText("Sorry, there was an error.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showNotMergedNotification(LocalBranchHelper nonmergedBranch) {
        logger.warn("Not merged notification");
        notificationPane.setText("That branch has to be merged before you can do that.");

        Action forceDeleteAction = new Action("Force delete", e -> {
            this.forceDeleteLocalBranch(nonmergedBranch);
            notificationPane.hide();
        });

        notificationPane.getActions().clear();
        notificationPane.getActions().setAll(forceDeleteAction);
        notificationPane.show();
    }

    private void showCannotDeleteBranchNotification(LocalBranchHelper branch) {
        logger.warn("Cannot delete current branch notification");
        notificationPane.setText(String.format("Sorry, %s can't be deleted right now. " +
                "Try checking out a different branch first.", branch.getBranchName()));
        // probably because it's checked out

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showRefAlreadyExistsNotification() {
        logger.info("Branch already exists notification");
        notificationPane.setText("Looks like that branch already exists locally!");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showInvalidBranchNameNotification() {
        logger.warn("Invalid branch name notification");
        notificationPane.setText("That branch name is invalid.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showConflictsNotification() {
        logger.info("Merge conflicts notification");
        notificationPane.setText("That merge resulted in conflicts. Check the working tree to resolve them.");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private void showNoCommitsYetNotification() {
        logger.warn("No commits yet notification");
        notificationPane.setText("You cannot make a branch since your repo has no commits yet. Make a commit first!");

        notificationPane.getActions().clear();
        notificationPane.show();
    }

    private static void showCheckoutConflictsNotification(List<String> conflictingPaths) {
        Platform.runLater(() -> {
            notificationPane.setText("You can't switch to that branch because there would be a merge conflict. Stash your changes or resolve conflicts first.");

            Action seeConflictsAction = new Action("See conflicts", e -> {
                notificationPane.hide();
                PopUpWindows.showCheckoutConflictsAlert(conflictingPaths);
            });

            notificationPane.getActions().clear();
            notificationPane.getActions().setAll(seeConflictsAction);

            notificationPane.show();
        });
    }

    /// END: ERROR NOTIFICATIONS ^^^
}