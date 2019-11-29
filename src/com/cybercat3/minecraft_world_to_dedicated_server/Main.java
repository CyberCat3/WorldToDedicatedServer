package com.cybercat3.minecraft_world_to_dedicated_server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader myLoader = new FXMLLoader(getClass().getResource("window.fxml"));
        Parent root = myLoader.load();
        primaryStage.setTitle("World To Dedicated Server");
        final int WIDTH = 520;
        final int HEIGHT = 260;
        primaryStage.setScene(new Scene(root, WIDTH, HEIGHT));
        {
            Controller controller = myLoader.getController();
            controller.initialize(primaryStage);
        }
        primaryStage.setMinWidth(WIDTH);
        primaryStage.setMaxWidth(WIDTH);
        primaryStage.setMinHeight(HEIGHT);
        primaryStage.setMaxHeight(HEIGHT);
        primaryStage.show();
        primaryStage.getScene().setOnKeyTyped(event -> {
            System.out.println(primaryStage.getWidth());
            System.out.println(primaryStage.getHeight());
        });

    }


    public static void main(String[] args) throws IOException {
        launch(args);
    }
}
