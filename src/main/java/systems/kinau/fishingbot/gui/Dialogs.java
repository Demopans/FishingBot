package systems.kinau.fishingbot.gui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.network.mojangapi.Realm;

import javax.swing.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Dialogs {

    public static void showJavaFXNotWorking() {
        JOptionPane.showConfirmDialog(new JFrame(), "JavaFX seems to be not working properly on your computer!\nPlease look at the log.\n\n" +
                "You can still use the bot in headless (nogui) mode using the start argument -nogui.", "FishingBot", JOptionPane.DEFAULT_OPTION);
    }

    public static void showRealmsWorlds(List<Realm> possibleRealms, Consumer<Realm> callback) {
        setupJFX();
        List<String> realmNames = possibleRealms.stream().map(realm -> realm.getName() + " by " + realm.getOwner()).collect(Collectors.toList());
        Platform.runLater(() -> {
            Dialog dialog;
            if (possibleRealms.isEmpty()) {
                dialog = new Alert(Alert.AlertType.INFORMATION);

                dialog.setHeaderText(FishingBot.getI18n().t("dialog-realms-no-realms"));
                dialog.setContentText(FishingBot.getI18n().t("realms-no-realms"));
            } else {
                dialog = new ChoiceDialog<>(realmNames.get(0), realmNames);
                dialog.setHeaderText(FishingBot.getI18n().t("dialog-realms-select"));
            }

            dialog.setTitle(FishingBot.PREFIX);

            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);
            stage.getIcons().add(new Image(Dialogs.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));

            AtomicReference<Realm> returningRealm = new AtomicReference<>(null);

            try {
                Optional<String> result = dialog.showAndWait();

                result.ifPresent(s -> {
                    String name = s.split(" by ")[0];
                    String owner = s.split(" by ")[1];
                    Optional<Realm> optRealm = possibleRealms.stream()
                            .filter(realm -> realm.getName().equals(name))
                            .filter(realm -> realm.getOwner().equals(owner))
                            .findAny();
                    optRealm.ifPresent(returningRealm::set);
                });
            } catch (Throwable ignore) {
            }
            callback.accept(returningRealm.get());
        });
    }

    public static void showRealmsAcceptToS(Consumer<Boolean> callback) {
        setupJFX();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.NO, ButtonType.YES);
            alert.setTitle(FishingBot.PREFIX);

            alert.setHeaderText(FishingBot.getI18n().t("dialog-realms-tos-header"));
            alert.setContentText(FishingBot.getI18n().t("dialog-realms-tos-content", "https://www.minecraft.net/en-us/realms/terms"));

            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);
            stage.getIcons().add(new Image(Dialogs.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));

            Optional<ButtonType> buttonType = alert.showAndWait();
            buttonType.ifPresent(buttonType1 -> callback.accept(buttonType1 == ButtonType.YES));
        });
    }

    private static void setupJFX() {
        final CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            new JFXPanel();
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException ignore) {
        }
    }

    public static void showAboutWindow(Stage parent, Consumer<String> callBack) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(FishingBot.PREFIX);

            alert.setHeaderText(FishingBot.getI18n().t("dialog-about-header"));
            FlowPane fp = new FlowPane();
            Label lbl = new Label(FishingBot.getI18n().t("dialog-about-content"));
            Hyperlink link = new Hyperlink(" faithful.team");
            fp.getChildren().addAll(lbl, link);

            link.setOnAction(event -> {
                alert.close();
                callBack.accept("https://faithful.team/");
            });

            alert.getDialogPane().contentProperty().set(fp);

            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.initOwner(parent);
            stage.getIcons().add(new Image(Dialogs.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));

            alert.showAndWait();
        });
    }

    public static void showCredentialsInvalid(Consumer<String> callBack) {
        setupJFX();

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(FishingBot.PREFIX);

            alert.setHeaderText(FishingBot.getI18n().t("dialog-credentials-invalid-header"));
            FlowPane fp = new FlowPane();
            Label lbl = new Label(FishingBot.getI18n().t("dialog-credentials-invalid-content"));
            Hyperlink link = new Hyperlink(" FishingBot Wiki");
            fp.getChildren().addAll(lbl, link);

            link.setOnAction(event -> {
                alert.close();
                callBack.accept("https://github.com/MrKinau/FishingBot/wiki/Troubleshooting");
            });

            alert.getDialogPane().contentProperty().set(fp);

            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(Dialogs.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));

            alert.showAndWait();
        });
    }

    public static void showAuthorizationRequest(String code, String url) {
        setupJFX();

        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle(FishingBot.PREFIX);

            alert.setHeaderText(FishingBot.getI18n().t("dialog-authorization-header"));
            FlowPane flowPane = new FlowPane();

            TextArea textArea = new TextArea(FishingBot.getI18n().t("auth-create-refresh-token", code, url));
            textArea.setEditable(false);
            textArea.setWrapText(true);
            flowPane.getChildren().add(textArea);

            alert.getDialogPane().contentProperty().set(flowPane);

            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(Dialogs.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));

            alert.show();
        });
    }

}
