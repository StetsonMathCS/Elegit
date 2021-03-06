package elegit;

import javafx.application.Platform;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A subclass of the RepoFile class that holds a reference to
 * and interacts with a file in the repository that was conflicting
 * but was then modified after the user was informed of the conflict
 */
public class ConflictingThenModifiedRepoFile extends RepoFile {

    private String resultType;

    public ConflictingThenModifiedRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        diffButton.setText("CONFLICTING\nMODIFIED");
        diffButton.setId("conflictingThenModifiedDiffButton");
        diffButton.setTooltip(getToolTip("This file was conflicting, but was recently modified.\nCommit if the changes are finalized."));
    }

    public ConflictingThenModifiedRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
    }

    @Override public boolean canAdd() throws GitAPIException {
        ReentrantLock lock = new ReentrantLock();
        Condition finishedAlert = lock.newCondition();

        Platform.runLater(() -> {
            logger.warn("Notification about conflicting the modified file");
            lock.lock();
            try{
                resultType = PopUpWindows.showAddingingConflictingThenModifiedFileAlert();
                finishedAlert.signal();
            }finally{
                lock.unlock();
            }
        });

        lock.lock();
        try{
            finishedAlert.await();
            if(resultType.equals("add")){
                return true;
            }
        }catch(InterruptedException ignored){
        }finally{
            lock.unlock();
        }
        return false;
    }

    @Override public boolean canRemove() { return true; }
}

