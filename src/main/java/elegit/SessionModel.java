package main.java.elegit;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import main.java.elegit.exceptions.MissingRepoException;
import main.java.elegit.exceptions.NoOwnerInfoException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The singleton SessionModel stores all the Repos (contained in RepoHelper objects)
 * in the session and lets the user switch between repos.
 */
public class SessionModel {

    // Keys for preferences recall
    private static final String RECENT_REPOS_LIST_KEY = "RECENT_REPOS_LIST";
    private static final String LAST_OPENED_REPO_PATH_KEY = "LAST_OPENED_REPO_PATH";

    private RepoHelper currentRepoHelper;
    public ObjectProperty<RepoHelper> currentRepoHelperProperty;

    List<RepoHelper> allRepoHelpers;
    private static SessionModel sessionModel;

    // All RepoHelpers have their own owner. This
    //   is the default owner that new RepoHelpers will
    //   be initiated with:
    private RepoOwner defaultOwner;

    Preferences preferences;

    /**
     * @return the SessionModel object
     */
    public static SessionModel getSessionModel() {
        if (sessionModel == null) {
            sessionModel = new SessionModel();
        }
        return sessionModel;
    }

    /**
     * Private constructor for the SessionModel singleton
     */
    private SessionModel() {
        this.allRepoHelpers = new ArrayList<>();
        this.preferences = Preferences.userNodeForPackage(this.getClass());
        currentRepoHelperProperty = new SimpleObjectProperty<>(currentRepoHelper);
    }

    /**
     * Loads the repository (from its RepoHelper) that was open when the app was
     * last closed. If this repo has been moved or deleted, it doesn't load anything.
     *
     * Uses the Java Preferences API (wrapped in IBM's PrefObj class) to load the repo.
     */
    public void loadMostRecentRepoHelper() {
        try{
        String lastOpenedRepoPathString = (String) PrefObj.getObject(this.preferences, LAST_OPENED_REPO_PATH_KEY);
        if (lastOpenedRepoPathString != null) {
            Path path = Paths.get(lastOpenedRepoPathString);
            try {
                ExistingRepoHelper existingRepoHelper = new ExistingRepoHelper(path, this.defaultOwner);
                this.openRepoFromHelper(existingRepoHelper);
            } catch (IllegalArgumentException e) {
                // The most recent repo is no longer in the directory it used to be in,
                // so just don't load it.
            }catch(NoOwnerInfoException | GitAPIException | MissingRepoException e){
                e.printStackTrace();
            }
        }
        }catch(IOException | BackingStoreException | ClassNotFoundException e){
            e.printStackTrace();
        }
    }

    /**
     * Loads all recently loaded repositories (stored with the Java Preferences API)
     * into the recent repos menubar.
     */
    public void loadRecentRepoHelpersFromStoredPathStrings() {
        try{
            ArrayList<String> storedRepoPathStrings = (ArrayList<String>) PrefObj.getObject(this.preferences, RECENT_REPOS_LIST_KEY);
            if (storedRepoPathStrings != null) {
                for (String pathString : storedRepoPathStrings) {
                    Path path = Paths.get(pathString);
                    try {
                        ExistingRepoHelper existingRepoHelper = new ExistingRepoHelper(path, this.defaultOwner);
                        this.allRepoHelpers.add(existingRepoHelper);
                    } catch (IllegalArgumentException e) {
                        // This happens when this repository has been moved.
                        // We'll just move along.
                    } catch(NoOwnerInfoException | GitAPIException e){
                        e.printStackTrace();
                    }
                }
            }
        } catch(IOException | ClassNotFoundException | BackingStoreException e){
            e.printStackTrace();
        }
    }

    /**
     * Adds a new repository (contained in a RepoHelper) to the session.
     *
     * @param anotherRepoHelper the RepoHelper to add.
     */
    public void addRepo(RepoHelper anotherRepoHelper) {
        this.allRepoHelpers.add(anotherRepoHelper);
    }

    /**
     * Opens a repository stored at a certain index in the list of
     * RepoHelpers.
     *
     * @param index the index of the repository to open.
     */
    public void openRepoAtIndex(int index) throws BackingStoreException, IOException, ClassNotFoundException {
        this.currentRepoHelper = this.allRepoHelpers.get(index);
        currentRepoHelperProperty.set(this.currentRepoHelper);
        this.saveListOfRepoPathStrings();
        this.saveMostRecentRepoPathString();
    }

    /**
     * Loads a RepoHelper by checking to see if that RepoHelper's directory is already
     * loaded into the Model. If it is already loaded, this method will load that RepoHelper.
     * If not, this method will add the new RepoHelper and then load it.
     *
     * @param repoHelperToLoad the RepoHelper to be loaded.
     */
    public void openRepoFromHelper(RepoHelper repoHelperToLoad) throws BackingStoreException, IOException, ClassNotFoundException, MissingRepoException{
        RepoHelper matchedRepoHelper = this.matchRepoWithAlreadyLoadedRepo(repoHelperToLoad);
        if (matchedRepoHelper == null) {
            // So, this repo isn't loaded into the model yet
            this.allRepoHelpers.add(repoHelperToLoad);
            this.openRepoAtIndex(this.allRepoHelpers.size() - 1);
        } else {
            // So, this repo is already loaded into the model
            if(matchedRepoHelper.exists()){
                this.openRepoAtIndex(this.allRepoHelpers.indexOf(matchedRepoHelper));
            }else{
                this.allRepoHelpers.remove(matchedRepoHelper);
                throw new MissingRepoException();
            }
        }
    }

    /**
     * Checks if a repoHelper is already loaded in the model by comparing repository directories.
     *
     * @param repoHelperCandidate the repoHelper being checked
     * @return the repo helper that points to the same repository as the candidate
     *          (by directory), or null if there is no such RepoHelper already in the model.
     */
    private RepoHelper matchRepoWithAlreadyLoadedRepo(RepoHelper repoHelperCandidate) {
        for (RepoHelper repoHelper : this.allRepoHelpers) {
            if (repoHelper.getLocalPath().equals(repoHelperCandidate.getLocalPath())) {
                return repoHelper;
            }
        }
        return null;
    }

    /**
     * @return the current repository
     */
    public RepoHelper getCurrentRepoHelper(){
        return currentRepoHelper;
    }

    /**
     * @return the current JGit repository associated with the current RepoHelper
     */
    public Repository getCurrentRepo() {
        return this.currentRepoHelper.getRepo();
    }

    /**
     * @return the default owner that will be assigned to new repositories
     */
    public RepoOwner getDefaultOwner() {
        return defaultOwner;
    }

    public void setCurrentDefaultOwner(RepoOwner newOwner) {
        this.defaultOwner = newOwner;
    }

    /**
     * Gets a list of all repositories held in this session. Repositories
     * that no longer exist are removed (and not returned)
     * @return a list of all existing repositories held in the session
     */
    public List<RepoHelper> getAllRepoHelpers() {
        List<RepoHelper> tempList = new ArrayList<>(allRepoHelpers);
        for(RepoHelper r : tempList){
            if(!r.exists()){
                allRepoHelpers.remove(r);
            }
        }
        return allRepoHelpers;
    }

    /**
     * Calls `git status` and returns the set of untracked files that Git reports.
     *
     * @return a set of untracked filenames in the working directory.
     * @throws GitAPIException if the `git status` call fails.
     */
    public Set<String> getUntrackedFiles() throws GitAPIException {
        Status status = new Git(this.getCurrentRepo()).status().call();
        Set<String> untrackedFiles = status.getUntracked();

        return untrackedFiles;
    }

    /**
     * Calls `git status` and returns the set of conflicting files that Git reports.
     *
     * @return a set of conflicting filenames in the working directory.
     * @throws GitAPIException
     */
    public Set<String> getConflictingFiles() throws GitAPIException {
        Status status = new Git(this.getCurrentRepo()).status().call();
        Set<String> conflictingFiles = status.getConflicting();

        return conflictingFiles;
    }

    /**
     * Calls `git status` and returns the set of missing files that Git reports.
     *
     * @return a set of missing filenames in the working directory.
     * @throws GitAPIException if the `git status` call fails.
     */
    public Set<String> getMissingFiles() throws GitAPIException {
        Status status = new Git(this.getCurrentRepo()).status().call();
        Set<String> missingFiles = status.getMissing();

        return missingFiles;
    }

    /**
     * Calls `git status` and returns the set of modified files that Git reports.
     *
     * @return a set of modified filenames in the working directory.
     * @throws GitAPIException if the `git status` call fails.
     */
    public Set<String> getModifiedFiles() throws GitAPIException {
        Status status = new Git(this.getCurrentRepo()).status().call();
        Set<String> modifiedFiles = status.getModified();

        return modifiedFiles;
    }

    /**
     * Get (construct) the current repo's working directory DirectoryRepoFile
     * by creating and populating a new DirectoryRepoFile from the repository's
     * parent directory.
     *
     * @return the populated DirectoryRepoFile for the current repository's parent directory.
     * @throws GitAPIException if the call to `populateDirectoryRepoFile(...)` fails.
     * @throws IOException if the call to `populateDirectoryRepoFile(...)` fails.
     */
    public DirectoryRepoFile getParentDirectoryRepoFile() throws GitAPIException, IOException {
        Path fullPath = this.currentRepoHelper.getLocalPath();

        DirectoryRepoFile parentDirectoryRepoFile = new DirectoryRepoFile(fullPath, this.getCurrentRepo());
        parentDirectoryRepoFile = this.populateDirectoryRepoFile(parentDirectoryRepoFile);

        return parentDirectoryRepoFile;
    }

    /**
     * Adds all the children files contained within a directory to that directory's DirectoryRepoFile.
     *
     * @param superDirectory the RepoFile of the directory to be populated.
     * @return the populated RepoFile of the initially passed-in directory.
     * @throws GitAPIException if the `git status` methods' calls to Git fail (for getting
     * @throws IOException if the DirectoryStream fails.
     */
    private DirectoryRepoFile populateDirectoryRepoFile(DirectoryRepoFile superDirectory) throws GitAPIException, IOException {
        try {
            DirectoryStream<Path> directoryStream = Files.newDirectoryStream(superDirectory.getFilePath());
            for (Path path : directoryStream) {
                if (path.equals(this.getCurrentRepo().getDirectory().toPath())) {

                    // If the path is the Repository's .git folder, don't populate it.

                } else if (Files.isDirectory(path)) {
                    // Recurse! Populate the directory.
                    DirectoryRepoFile subdirectory = new DirectoryRepoFile(path, superDirectory.getRepo());
                    populateDirectoryRepoFile(subdirectory);
                    superDirectory.addChild(subdirectory);

                } else {
                    // So, this is a file and not a directory.

                    Set<String> modifiedFiles = getModifiedFiles();
                    Set<String> missingFiles = getMissingFiles();
                    Set<String> untrackedFiles = getUntrackedFiles();
                    Set<String> conflictingFiles = getConflictingFiles();

                    // Relativize the path to the repository, because that's the file structure JGit
                    //  looks for in an 'add' command
                    Path repoDirectory = this.getCurrentRepo().getWorkTree().toPath();
                    Path relativizedPath = repoDirectory.relativize(path);

                    // Determine what type of RepoFile we're dealing with.
                    //  Is it modified? Untracked/new? Missing? Just a plain file?
                    //  Construct the appropriate RepoFile and add it to the parent directory.
                    if (conflictingFiles.contains(relativizedPath.toString())) {
                        ConflictingRepoFile conflictingFile = new ConflictingRepoFile(path, this.getCurrentRepo());
                        superDirectory.addChild(conflictingFile);
                    } else if (modifiedFiles.contains(relativizedPath.toString())) {
                        ModifiedRepoFile modifiedFile = new ModifiedRepoFile(path, this.getCurrentRepo());
                        superDirectory.addChild(modifiedFile);
                    } else if (missingFiles.contains(relativizedPath.toString())) {
                        MissingRepoFile missingFile = new MissingRepoFile(path, this.getCurrentRepo());
                        superDirectory.addChild(missingFile);
                    } else if (untrackedFiles.contains(relativizedPath.toString())) {
                        UntrackedRepoFile untrackedFile = new UntrackedRepoFile(path, this.getCurrentRepo());
                        superDirectory.addChild(untrackedFile);
                    } else {
                        RepoFile plainRepoFile = new RepoFile(path, this.getCurrentRepo());
                        superDirectory.addChild(plainRepoFile);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return superDirectory;
    }

    /**
     * Assembles all the changed files (modified, missing, untracked) into RepoFiles
     * and returns a list of them.
     *
     * @return a list of changed files, contained in RepoFile objects.
     * @throws GitAPIException if the `git status` calls fail.
     */
    public List<RepoFile> getAllChangedRepoFiles() throws GitAPIException {
        Set<String> modifiedFiles = getModifiedFiles();
        Set<String> missingFiles = getMissingFiles();
        Set<String> untrackedFiles = getUntrackedFiles();
        Set<String> conflictingFiles = getConflictingFiles();

        List<RepoFile> changedRepoFiles = new ArrayList<>();

        ArrayList<String> conflictingRepoFileStrings = new ArrayList<>();

        for (String conflictingFileString : conflictingFiles) {
            ConflictingRepoFile conflictingRepoFile = new ConflictingRepoFile(conflictingFileString, this.getCurrentRepo());
            changedRepoFiles.add(conflictingRepoFile);

            // Store these paths to make sure this file isn't registered as a modified file or something.
            //  If it's conflicting, the app should focus only on the conflicting state of the
            //  file first.
            //
            // e.g. If a modification causes a conflict, that file should have its conflicts resolved
            //      before it gets added.
            conflictingRepoFileStrings.add(conflictingFileString);
        }

        for (String modifiedFileString : modifiedFiles) {
            if (!conflictingRepoFileStrings.contains(modifiedFileString)) {
                ModifiedRepoFile modifiedRepoFile = new ModifiedRepoFile(modifiedFileString, this.getCurrentRepo());
                changedRepoFiles.add(modifiedRepoFile);
            }
        }

        for (String missingFileString : missingFiles) {
            if (!conflictingRepoFileStrings.contains(missingFileString)) {
                MissingRepoFile missingRepoFile = new MissingRepoFile(missingFileString, this.getCurrentRepo());
                changedRepoFiles.add(missingRepoFile);
            }
        }

        for (String untrackedFileString : untrackedFiles) {
            if (!conflictingRepoFileStrings.contains(untrackedFileString)) {
                UntrackedRepoFile untrackedRepoFile = new UntrackedRepoFile(untrackedFileString, this.getCurrentRepo());
                changedRepoFiles.add(untrackedRepoFile);
            }
        }

        return changedRepoFiles;
    }

    /**
     * Saves the model's list of RepoHelpers using the Preferences API (and the PrefObj wrapper
     *  from IBM).
     *
     * We store these as a list of file strings instead of Paths
     *  because Paths aren't serializable.
     *
     * @throws BackingStoreException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void saveListOfRepoPathStrings() throws BackingStoreException, IOException, ClassNotFoundException {
        ArrayList<String> repoPathStrings = new ArrayList<>();
        for (RepoHelper repoHelper : this.allRepoHelpers) {
            Path path = repoHelper.getLocalPath();
            repoPathStrings.add(path.toString());
        }

        // Store the list object using IBM's PrefObj helper class:
        PrefObj.putObject(this.preferences, RECENT_REPOS_LIST_KEY, repoPathStrings);
    }

    /**
     * Saves the most recently opened repository to the Preferences API (to be
     *  re-opened next time the app is opened).
     *
     * @throws BackingStoreException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void saveMostRecentRepoPathString() throws BackingStoreException, IOException, ClassNotFoundException {
        String pathString = this.currentRepoHelper.getLocalPath().toString();

        PrefObj.putObject(this.preferences, LAST_OPENED_REPO_PATH_KEY, pathString);
    }

    /**
     * Clears the information stored by the Preferences API:
     *  recent repos and the last opened repo.
     *
     * @throws BackingStoreException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void clearStoredPreferences() throws BackingStoreException, IOException, ClassNotFoundException {
        PrefObj.putObject(this.preferences, RECENT_REPOS_LIST_KEY, null);
        PrefObj.putObject(this.preferences, LAST_OPENED_REPO_PATH_KEY, null);
    }

    public void removeRepoHelpers(List<RepoHelper> checkedItems) {
        for (RepoHelper item : checkedItems) {
            this.allRepoHelpers.remove(item);
        }
    }
}
