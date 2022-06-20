package nl.mansoft.sasl;

import java.util.Optional;
import javax.json.JsonBuilderFactory;
import javax.security.sasl.SaslClient;
import java.util.Base64;
import javax.security.sasl.Sasl;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import javax.json.Json;
import nl.mansoft.browserextension.NativeMessaging;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import java.io.PrintWriter;
import javax.json.JsonValue;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonObject;
import javax.security.sasl.RealmCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.Callback;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.application.Application;

/**
 *
 * @author hfmanson@gmail.com
 */
// JavaFX login dialog based on https://code.makery.ch/blog/javafx-dialogs-official/
public class Client extends Application {

    private static class MyResult {

        private final String username;
        private final String password;
        private final String mechanism;

        public MyResult(final String username, final String password, final String mechanism) {
            this.username = username;
            this.password = password;
            this.mechanism = mechanism;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getMechanism() {
            return mechanism;
        }
    }
    private Dialog<MyResult> dialog;
    private String username;
    private String password;
    private String mechanism;
    private ChoiceBox mechanismsBox;

    public void processCallbacks(final Callback[] callbacks) {
        for (final Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                final NameCallback nameCallback = (NameCallback) callback;
                nameCallback.setName(nameCallback.getDefaultName());
            } else if (callback instanceof PasswordCallback) {
                final PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(password.toCharArray());
            } else if (callback instanceof RealmCallback) {
                final RealmCallback realmCallback = (RealmCallback) callback;
                System.err.println("RealmCallback: " + realmCallback.getDefaultText());
                realmCallback.setText(realmCallback.getDefaultText());
            }
        }
    }

    public static String jsonGetString(final JsonObject jsonObject, final String name) {
        final JsonString jsonString = jsonObject.getJsonString(name);
        return (jsonString == null) ? null : jsonString.getString();
    }

    public static JsonObject jsonGetObject(final JsonObject jsonObject, final String name) {
        final JsonObject jsonObjectOut = jsonObject.getJsonObject(name);
        return jsonObjectOut;
    }

    public static boolean addString(final JsonObjectBuilder builder, final JsonObject jsonObject, final String name) {
        final String value = jsonGetString(jsonObject, name);
        final boolean result = value != null;
        if (result) {
            builder.add(name, value);
        }
        return result;
    }

    public static boolean addObject(final JsonObjectBuilder builder, final JsonObject jsonObject, final String name) {
        final JsonObject value = jsonGetObject(jsonObject, name);
        final boolean result = value != null;
        if (result) {
            builder.add(name, (JsonValue) value);
        }
        return result;
    }

    public static void printArgs(final PrintWriter logwriter, final String[] args) {
        if (args.length == 0) {
            System.err.println("No arguments");
        } else {
            for (final String arg : args) {
                System.err.println(arg);
            }
        }
    }

    public static void main(final String[] args) {
        launch(args);
    }

    private void createDialog() {
        dialog = new Dialog<>();
        dialog.setTitle("Login Dialog");
        final ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        final GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(20.0, 150.0, 10.0, 10.0));
        final TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        final PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        mechanismsBox = new ChoiceBox();
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Mechanism:"), 0, 2);
        grid.add(mechanismsBox, 1, 2);
        final Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> loginButton.setDisable(newValue.trim().isEmpty()));
        dialog.getDialogPane().setContent((Node) grid);
        dialog.setResizable(true);
        Platform.runLater(() -> usernameField.requestFocus());
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new MyResult(usernameField.getText(), passwordField.getText(), (String) mechanismsBox.getValue());
            }
            return null;
        });
    }

    @Override
    public void start(final Stage primaryStage) {
        try {
            System.err.println("STARTING");
            createDialog();
            SaslClient sc = null;
            final NativeMessaging nativeMessaging = new NativeMessaging();
            JsonObject inputJson;
            while ((inputJson = nativeMessaging.readMessage()) != null) {
                System.err.println(inputJson);
                final JsonBuilderFactory factory = Json.createBuilderFactory((Map) null);
                final JsonObjectBuilder outputJsonBuilder = factory.createObjectBuilder();
                final String mech = jsonGetString(inputJson, "mech");
                if (mech != null) {
                    System.err.println("creating SASL client, mechanisms: " + mech);
                    mechanismsBox.getItems().clear();
                    mechanismsBox.getItems().addAll((Collection) new HashSet(Arrays.asList(mech.split(" "))));
                    if (sc != null) {
                        System.err.println("disposing previous SASL client");
                        sc.dispose();
                    }
                    final String realm = jsonGetString(inputJson, "realm");
                    if (realm != null) {
                        dialog.setHeaderText(realm);
                    }
                    final Optional<MyResult> dialogResult = (Optional<MyResult>) dialog.showAndWait();
                    dialogResult.ifPresent(result -> {
                        System.err.println("Username=" + result.getUsername() + ", Password=" + result.getPassword() + ", Mechanism=" + result.getMechanism());
                        username = result.getUsername();
                        password = result.getPassword();
                        mechanism = result.getMechanism();
                    });
                    final JsonObject extraInfoSpec = jsonGetObject(inputJson, "extraInfoSpec");
                    String serverName = "localhost";
                    if (extraInfoSpec != null) {
                        final String redirectUrl = jsonGetString(extraInfoSpec, "redirectUrl");
                        if (redirectUrl != null) {
                            final URL url = new URL(redirectUrl);
                            serverName = url.getHost();
                            System.err.println("serverName: " + serverName);
                        } else {
                            System.err.println("redirectUrl not found");
                        }
                    } else {
                        System.err.println("extraInfoSpec not found");
                    }
                    final String[] mechanisms = {mechanism};
                    sc = Sasl.createSaslClient(mechanisms, username, "http", serverName, null, (final Callback[] callbacks) -> {
                        Client.this.processCallbacks(callbacks);
                    });
                    outputJsonBuilder.add("mech", sc.getMechanismName());
                }
                addString(outputJsonBuilder, inputJson, "requestId");
                addObject(outputJsonBuilder, inputJson, "extraInfoSpec");
                addString(outputJsonBuilder, inputJson, "s2s");
                final String s2cBase64 = jsonGetString(inputJson, "s2c");
                if (s2cBase64 != null) {
                    final byte[] challenge = Base64.getDecoder().decode(s2cBase64);
                    System.err.println(new String(challenge));
                    final byte[] response = sc.evaluateChallenge(challenge);
                    if (response == null) {
                        System.err.println("response is null");
                    } else {
                        System.err.println(new String(response));
                        final String c2s = Base64.getEncoder().encodeToString(response);
                        outputJsonBuilder.add("c2s", c2s);
                    }
                    if (sc.isComplete()) {
                        System.err.println("sc.isComplete()");
                        sc.dispose();
                        sc = null;
                    }
                }
                final JsonObject outputJson = outputJsonBuilder.build();
                nativeMessaging.writeMessage(outputJson);
                final String output = outputJson.toString();
                System.err.println(output);
            }
            System.err.println("EXITING");
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
