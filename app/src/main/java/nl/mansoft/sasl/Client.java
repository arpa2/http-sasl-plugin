/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mansoft.sasl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Pair;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import nl.mansoft.browserextension.NativeMessaging;
/**
 *
 * @author hfmanson@gmail.com
 */

// JavaFX login dialog based on https://code.makery.ch/blog/javafx-dialogs-official/

public class Client extends Application {
    private Dialog<Pair<String, String>> dialog;
    private String authorizationId;
    private String password;

    public void processCallbacks(Callback[] callbacks) {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nameCallback = (NameCallback) callback;
                nameCallback.setName(nameCallback.getDefaultName());
            } else if (callback instanceof PasswordCallback) {
                PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(password.toCharArray());
            } else if (callback instanceof RealmCallback) {
                RealmCallback realmCallback = (RealmCallback) callback;
                realmCallback.setText(realmCallback.getDefaultText());
            }
        }
    }

    public static String jsonGetString(JsonObject jsonObject, String name) {
        JsonString jsonString = jsonObject.getJsonString(name);
        return jsonString == null ? null : jsonString.getString();
    }

    public static JsonObject jsonGetObject(JsonObject jsonObject, String name) {
        JsonObject jsonObjectOut = jsonObject.getJsonObject(name);
        return  jsonObjectOut;
    }

    public static boolean addString(JsonObjectBuilder builder, JsonObject jsonObject, String name) {
        String value = jsonGetString(jsonObject, name);
        boolean result = value != null;
        if (result) {
            builder.add(name, value);
        }
        return result;
    }

    public static boolean addObject(JsonObjectBuilder builder, JsonObject jsonObject, String name) {
        JsonObject value = jsonGetObject(jsonObject, name);
        boolean result = value != null;
        if (result) {
            builder.add(name, value);
        }
        return result;
    }

    public static void printArgs(PrintWriter logwriter, String[] args) {
        if (args.length == 0) {
            System.err.println("No arguments");
        } else {
            for (String arg: args) {
                System.err.println(arg);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void createDialog() {
// Create the custom dialog.
        dialog = new Dialog<>();
        dialog.setTitle("Login Dialog");

// Set the icon (must be included in the project).
//dialog.setGraphic(new ImageView(this.getClass().getResource("login.png").toString()));
// Set the button types.
        ButtonType loginButtonType = new ButtonType("Login", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

// Create the username and password labels and fields.
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);

// Enable/Disable login button depending on whether a username was entered.
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

// Do some validation (using the Java 8 lambda syntax).
        username.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });

        dialog.getDialogPane().setContent(grid);
        dialog.setResizable(true);
// Request focus on the username field by default.
        Platform.runLater(() -> username.requestFocus());

// Convert the result to a username-password-pair when the login button is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(username.getText(), password.getText());
            }
            return null;
        });
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            System.err.println("STARTING");
            createDialog();
            JsonObject inputJson;
            String mechanism = "DIGEST-MD5";
            SaslClient sc = null;
            NativeMessaging nativeMessaging = new NativeMessaging();
            while ((inputJson = nativeMessaging.readMessage()) != null) {
                System.err.println(inputJson);
                String realm = jsonGetString(inputJson, "realm");
                if (!inputJson.containsKey("s2s")) {
                    System.err.println("no realm, creating SASL client");
                    if (sc != null) {
                        System.err.println("disposing previous SASL client");
                        sc.dispose();
                    }

                    dialog.setHeaderText(realm);

                    Optional<Pair<String, String>> result = dialog.showAndWait();
                    result.ifPresent(usernamePassword -> {
                        System.err.println("Username=" + usernamePassword.getKey() + ", Password=" + usernamePassword.getValue());
                        authorizationId = usernamePassword.getKey();
                        password = usernamePassword.getValue();
                    });

                    sc = Sasl.createSaslClient(new String[] { mechanism }, authorizationId, "http", realm, null, new CallbackHandler() {
                        @Override
                        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                            processCallbacks(callbacks);
                        }
                    });
                }
                JsonBuilderFactory factory = Json.createBuilderFactory(null);
                JsonObjectBuilder outputJsonBuilder = factory.createObjectBuilder()
                    .add("mech", "DIGEST-MD5");
                outputJsonBuilder.add("realm", realm);
                addString(outputJsonBuilder, inputJson, "requestId");
                addObject(outputJsonBuilder, inputJson, "extraInfoSpec");
                addString(outputJsonBuilder, inputJson, "s2s");
                String s2cBase64 = jsonGetString(inputJson, "s2c");
                if (s2cBase64 != null) {
                    byte[] challenge = Base64.getDecoder().decode(s2cBase64);
                    System.err.println(new String(challenge));
                    byte[] response = sc.evaluateChallenge(challenge);
                    if (response == null) {
                        System.err.println("response is null");
                        if (sc.isComplete()) {
                            System.err.println("sc.isComplete()");
                            sc.dispose();
                            sc = null;
                        }
                    } else {
                        System.err.println(new String(response));
                        String c2s = Base64.getEncoder().encodeToString(response);
                        outputJsonBuilder.add("c2s", c2s);
                    }
                }
                JsonObject outputJson = outputJsonBuilder.build();
                nativeMessaging.writeMessage(outputJson);
                String output = outputJson.toString();
                System.err.println(output);
            }
            System.err.println("EXITING");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
