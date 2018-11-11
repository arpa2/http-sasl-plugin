/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mansoft.sasl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
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
import javax.json.stream.JsonParser;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
/**
 *
 * @author hfmanson@gmail.com
 */

// JavaFX login dialog based on https://code.makery.ch/blog/javafx-dialogs-official/

public class Client extends Application {
    private Dialog<Pair<String, String>> dialog;
    private String password;

    public static int readInt32() throws IOException {
        int byte1 = System.in.read();
        if (byte1 == -1) {
            return -1;
        }
        int byte2 = System.in.read() << 8;
        if (byte2 == -1) {
            return -1;
        }
        int byte3 = System.in.read() << 16;
        if (byte3 == -1) {
            return -1;
        }
        int byte4 = System.in.read() << 24;
        if (byte4 == -1) {
            return -1;
        }
        return byte1 | byte2 | byte3 | byte4;
    }

    public static void writeInt32(int length) throws IOException {
        System.out.write(length);
        System.out.write(length >> 8);
        System.out.write(length >> 16);
        System.out.write(length >> 24);
    }

    public void processCallbacks(Callback[] callbacks) {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nameCallback = (NameCallback) callback;
                Optional<Pair<String, String>> result = dialog.showAndWait();
                dialog.close();
                result.ifPresent(usernamePassword -> {
                    //System.out.println("Username=" + usernamePassword.getKey() + ", Password=" + usernamePassword.getValue());
                    nameCallback.setName(usernamePassword.getKey());
                    password = usernamePassword.getValue();
                });

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

    public static boolean addString(JsonObjectBuilder builder, JsonObject jsonObject, String name) {
        String value = jsonGetString(jsonObject, name);
        boolean result = value != null;
        if (result) {
            builder.add(name, value);
        }
        return result;
    }

    public static void printArgs(PrintWriter logwriter, String[] args) {
        if (args.length == 0) {
            logwriter.println("No arguments");
        } else {
            for (String arg: args) {
                logwriter.println(arg);
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
        dialog.setHeaderText("Look, a Custom Login Dialog");

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
        try (FileWriter fw = new FileWriter("log.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter logwriter = new PrintWriter(bw, true))
        {
            try {
                logwriter.println("STARTING");
                //printArgs(logwriter, args);
                createDialog();

                int len;
                String mechanism = "DIGEST-MD5";
                SaslClient sc = null;
                while ((len = readInt32()) != -1) {
                    byte[] message = new byte[len];
                    System.in.read(message);
                    String str = new String(message, "UTF-8");
                    StringReader reader = new StringReader(str);
                    JsonParser parser = Json.createParser(reader);
                    parser.next();
                    JsonObject inputJson = parser.getObject();
                    logwriter.println(inputJson);
                    if (!inputJson.containsKey("s2s")) {
                        logwriter.println("no realm, creating SASL client");
                        if (sc != null) {
                            logwriter.println("disposing previous SASL client");
                            sc.dispose();
                        }
                        sc = Sasl.createSaslClient(new String[] { mechanism }, "henri", "http", "test-realm.nl", null, new CallbackHandler() {
                            @Override
                            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                                processCallbacks(callbacks);
                            }
                        });
                    }
                    JsonBuilderFactory factory = Json.createBuilderFactory(null);
                    JsonObjectBuilder outputJsonBuilder = factory.createObjectBuilder()
                        .add("mech", "DIGEST-MD5");
                    addString(outputJsonBuilder, inputJson, "realm");
                    addString(outputJsonBuilder, inputJson, "s2s");
                    String s2cBase64 = jsonGetString(inputJson, "s2c");
                    if (s2cBase64 != null) {
                        byte[] challenge = Base64.getDecoder().decode(s2cBase64);
                        logwriter.println(new String(challenge));
                        byte[] response = sc.evaluateChallenge(challenge);
                        if (response == null) {
                            logwriter.println("response is null");
                            if (sc.isComplete()) {
                                logwriter.println("sc.isComplete()");
                                sc.dispose();
                                sc = null;
                            }
                        } else {
                            logwriter.println(new String(response));
                            String c2s = Base64.getEncoder().encodeToString(response);
                            outputJsonBuilder.add("c2s", c2s);
                        }
                    }
                    JsonObject outputJson = outputJsonBuilder.build();
                    String output = outputJson.toString();
                    logwriter.println(output);
                    writeInt32(output.length());
                    System.out.print(output);
                }
                logwriter.println("EXITING");
            } catch (Exception e) {
                e.printStackTrace(logwriter);
            }
        } catch (IOException e2) {
            //exception handling left as an exercise for the reader
        }
    }
}
