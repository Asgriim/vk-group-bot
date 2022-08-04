import app.App;
import java.io.IOException;
import java.util.logging.LogManager;

public class Main {
    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("logConfiguration.properties"));
            new App().launch();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }
}

