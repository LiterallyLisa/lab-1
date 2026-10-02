package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.gui.MainView;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        view.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}