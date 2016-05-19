package elegit;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
/**
 * The starting point for this JavaFX application.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{

        // -----------------------Logging Initialization Start---------------------------
        // TODO change this to be placed outside generated JAR-file
        //System.setProperty("logFolder", getClass().getResource("logs/").getPath().toString());

        final Logger logger = LogManager.getLogger();
        // -----------------------Logging Initialization End-----------------------------

        logger.info("Starting up.");


        Pane root = FXMLLoader.load(getClass().getResource
                ("/elegit/fxml/MainView.fxml"));
        primaryStage.setTitle("Elegit");

        // sets the icon
        Image img = new Image(getClass().getResourceAsStream("/elegit/elegit_icon.png"));
        primaryStage.getIcons().add(img);
        // handles mac os dock icon
        if (SystemUtils.IS_OS_MAC) {
            java.awt.image.BufferedImage dock_img = ImageIO.read(
                    getClass().getResourceAsStream(
                    "/elegit/elegit_icon.png"
                )
            );
            com.apple.eawt.Application.getApplication()
                    .setDockIconImage(dock_img);
        }

        primaryStage.setOnCloseRequest(event -> logger.info("Closed"));

        BusyWindow.setParentWindow(primaryStage);

        Scene scene = new Scene(root, 1200, 650); // width, height

        // create the menu bar here
        MenuBar menuBar = MenuPopulator.getInstance().populate();
        // for now we'll only display menu on mac os
        // because it blocks repo dropdown menu on other platforms
        if (SystemUtils.IS_OS_MAC) {
            ((Pane) scene.getRoot()).getChildren().addAll(menuBar);
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
