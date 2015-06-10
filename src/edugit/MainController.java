package edugit;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;

import java.io.File;

public class MainController extends Controller{

    public final String defaultPath = System.getProperty("user.home")+File.separator+"Desktop"+File.separator+"TestClone";

    @FXML private Tab addFileTab;
    @FXML private Tab createRepoTab;

    private File selectedRepo;
    private File selectedFile;

    @FXML private Text messageText;
    @FXML private Label repoPathLabel;
    @FXML private Label fileNameLabel;
    @FXML private TextField commitText;

    @FXML private Button chooseRepoButton;
    @FXML private Button chooseFileButton;
    @FXML private Button commitButton;

    private File newRepo;

    @FXML private Label createRepoPathLabel;
    @FXML private TextField createRepoNameText;
    @FXML private Text createMessageText;
    @FXML private Button createRepoPathButton;
    @FXML private Button createButton;

    public MainController(){
    }

    @FXML private void updateButtonDisables() {
        if(addFileTab.isSelected()) {
            if (ThreadHelper.isProgressThreadRunning()) {
                chooseRepoButton.setDisable(true);
                chooseFileButton.setDisable(true);
                commitButton.setDisable(true);
            } else {
                chooseRepoButton.setDisable(false);
                chooseFileButton.setDisable(selectedRepo == null);
                commitButton.setDisable(selectedRepo == null || selectedFile == null);
            }
        }else if(createRepoTab.isSelected()){
            if (ThreadHelper.isProgressThreadRunning()) {
                createRepoPathButton.setDisable(true);
                createButton.setDisable(true);
            } else {
                createRepoPathButton.setDisable(false);
                createButton.setDisable(newRepo == null || createRepoNameText.getText().equals(""));
            }
        }
    }

    public void handleCommitButtonAction(ActionEvent actionEvent) {
        Task task = new Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return addFileAndPush();
            }
        };
        Thread th = new Thread(task);
        th.setDaemon(false);
        th.start();
    }

    public void handleCreateButtonAction(ActionEvent actionEvent) {
        Task task = new Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return cloneRepoAndPush();
            }
        };
        Thread th = new Thread(task);
        th.setDaemon(false);
        th.start();
    }

    private boolean cloneRepoAndPush() {
//        threadHelper.startProgressThread(this.createMessageText);

        this.updateButtonDisables();

        String repoPath = createRepoPathLabel.getText();
        String repoName = createRepoNameText.getText();

        NewRepoHelper repo;

        try {
            repo = new NewRepoHelper(new File(repoPath, repoName).toPath(), SECRET_CONSTANTS.TEST_GITHUB_TOKEN);
        }catch (Exception e){
            e.printStackTrace();
            ThreadHelper.endProgressThread();
            this.updateButtonDisables();
            return false;
        }

        repo.closeRepo();

        ThreadHelper.endProgressThread();

        this.createMessageText.setText(repoName + " created");

        this.updateButtonDisables();

        return true;
    }

    private boolean addFileAndPush(){
//        threadHelper.startProgressThread(this.messageText);

        this.updateButtonDisables();

        String repoPath = repoPathLabel.getText();
        String filePath = fileNameLabel.getText();
        String commitMessage = commitText.getText();

        ClonedRepoHelper repo;

        try {
            repo = new ClonedRepoHelper(new File(repoPath).toPath(), SECRET_CONSTANTS.TEST_GITHUB_TOKEN);
        }catch (Exception e){
            e.printStackTrace();
            ThreadHelper.endProgressThread();
            this.updateButtonDisables();
            return false;
        }

//        repo.pushNewFile(new File(filePath), commitMessage);

        repo.closeRepo();

        ThreadHelper.endProgressThread();

        this.messageText.setText(filePath + " added");

        this.updateButtonDisables();

        return true;
    }

    public void handleChooseRepoLocationButton(ActionEvent actionEvent) {
        File newSelectedRepo = this.getPathFromChooser(true, "Git Repositories",this.messageText.getScene().getWindow());

        if(newSelectedRepo != null){
            this.selectedRepo = newSelectedRepo;
            this.repoPathLabel.setText(selectedRepo.toString());
            this.updateButtonDisables();
        }
    }

    public void handleChooseFileButton(ActionEvent actionEvent) {
        File newSelectedFile = this.getPathFromChooser(false, "Repository Files",this.messageText.getScene().getWindow());

        if(newSelectedFile != null){
            this.selectedFile = newSelectedFile;
            this.fileNameLabel.setText(getRelativePath(selectedRepo, selectedFile));
            this.updateButtonDisables();
        }
    }

    public void handleCreateRepoLocationButton(ActionEvent actionEvent) {
        File newRepo = this.getPathFromChooser(true, "Choose a location",this.messageText.getScene().getWindow());

        if(newRepo != null){
            this.newRepo = newRepo;
            this.createRepoPathLabel.setText(newRepo.toString());
            this.updateButtonDisables();
        }
    }
}
