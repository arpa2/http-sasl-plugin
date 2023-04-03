package nl.mansoft.sasl;

import java.util.Optional;
import javax.json.JsonBuilderFactory;
import javax.security.sasl.SaslClient;
import java.util.Base64;
import javax.security.sasl.Sasl;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
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
import java.security.Principal;
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
import javafx.collections.ObservableList;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

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
    private LoginContext context;

    private static final String getHexBytes(byte[] bytes, int pos, int len) {

        StringBuffer sb = new StringBuffer();
        for (int i = pos; i < (pos+len); i++) {

            int b1 = (bytes[i]>>4) & 0x0f;
            int b2 = bytes[i] & 0x0f;

            sb.append(Integer.toHexString(b1));
            sb.append(Integer.toHexString(b2));
            sb.append(' ');
        }
        return sb.toString();
    }

    private static final String getHexBytes(byte[] bytes) {
        return getHexBytes(bytes, 0, bytes.length);
    }

    public static String bytesToString(byte[] bytes) {
        boolean allAscii = true;
        for (byte b : bytes) {
            if (b < 0x20) {
                allAscii = false;
                break;
            }
        }
        return allAscii ? new String(bytes) : getHexBytes(bytes);
    }

    public static void printBytes(String prompt, byte[] bytes) {
        System.err.println(prompt + ": " + bytesToString(bytes));
    }

    class MyCallbackHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (final Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    final NameCallback nameCallback = (NameCallback) callback;
                    nameCallback.setName(username);
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

    SaslClient createSaslClient(JsonObjectBuilder outputJsonBuilder, String mechanism, String serverName) throws SaslException {
        SaslClient sc = Sasl.createSaslClient(new String[] { mechanism }, null, "HTTP", serverName, null, new MyCallbackHandler());
        if (sc.hasInitialResponse()) {
            System.err.println("hasInitialResponse");
            final byte[] challenge = new byte[] { };
            final byte[] response = sc.evaluateChallenge(challenge);
            if (response == null) {
                System.err.println("response is null");
            } else {
                printBytes("response", response);
                final String c2s = Base64.getEncoder().encodeToString(response);
                outputJsonBuilder.add("c2s", c2s);
            }
        }
        return sc;
    }

    class CreateSaslClient implements PrivilegedExceptionAction<SaslClient> {
        private final JsonObjectBuilder outputJsonBuilder;
        private final String mechanism;
        private final String serverName;

        CreateSaslClient(JsonObjectBuilder outputJsonBuilder, String mechanism, String serverName) {
            this.outputJsonBuilder = outputJsonBuilder;
            this.mechanism = mechanism;
            this.serverName = serverName;
        }

        @Override
        public SaslClient run() throws Exception {
            return createSaslClient(outputJsonBuilder, mechanism, serverName);
        }
    }

    class EvaluateChallenge implements PrivilegedExceptionAction<byte[]>{
        private final SaslClient saslClient;
        private final byte[] challenge;

        public EvaluateChallenge(SaslClient saslClient, byte[] challenge) {
            this.saslClient = saslClient;
            this.challenge = challenge;
        }

        //saslServer.evaluateResponse
        @Override
        public byte[] run() throws SaslException {
            return saslClient.evaluateChallenge(challenge);
        }
    }

    private Subject getSubject() {
        return context == null ? null : context.getSubject();
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
                addString(outputJsonBuilder, inputJson, "requestId");
                final String mech = jsonGetString(inputJson, "mech");
                if (mech != null) {
                    System.err.println("creating SASL client, mechanisms: " + mech);
                    ObservableList<String> items = mechanismsBox.getItems();
                    items.clear();
                    items.addAll(new HashSet(Arrays.asList(mech.split(" "))));
                    mechanismsBox.setValue(items.get(0));

                    if (sc != null) {
                        System.err.println("disposing previous SASL client");
                        sc.dispose();
                    }
                    final String realm = jsonGetString(inputJson, "realm");
                    if (realm != null) {
                        dialog.setHeaderText(realm);
                    }
                    final Optional<MyResult> dialogResult = dialog.showAndWait();
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
                    if (mechanism.equals("GSSAPI")) {
                        try {
                            context = new LoginContext("client", new MyCallbackHandler());
                            context.login();
                            String principals = "Authenticated principals: ";
                            for (Principal principal : context.getSubject().getPrincipals()) {
                                principals += principal.getName() + " ";
                            }
                            System.err.println(principals);
                        } catch (LoginException ex) {
                            System.err.println("LoginException");
                            context = null;
                            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, ex.getMessage());
                            outputJsonBuilder.add("extraInfoSpec", JsonValue.EMPTY_JSON_OBJECT);
                            final JsonObject outputJson = outputJsonBuilder.build();
                            nativeMessaging.writeMessage(outputJson);
                            final String output = outputJson.toString();
                            System.err.println(output);
                            continue;
                        }
                    }
                    sc = Subject.doAs(getSubject(), new CreateSaslClient(outputJsonBuilder, mechanism, serverName));
                    outputJsonBuilder.add("mech", sc.getMechanismName());
                }
                addObject(outputJsonBuilder, inputJson, "extraInfoSpec");
                addString(outputJsonBuilder, inputJson, "s2s");
                final String s2cBase64 = jsonGetString(inputJson, "s2c");
                if (sc != null && s2cBase64 != null) {
                    final byte[] challenge = Base64.getDecoder().decode(s2cBase64);
                    printBytes("challenge", challenge);
                    final byte[] response = Subject.doAs(getSubject(), new EvaluateChallenge(sc, challenge));
                    if (response == null) {
                        System.err.println("response is null");
                    } else {
                        printBytes("response", response);
                        final String c2s = Base64.getEncoder().encodeToString(response);
                        outputJsonBuilder.add("c2s", c2s);
                    }
                    if (sc.isComplete()) {
                        System.err.println("sc.isComplete()");
                        sc.dispose();
                        sc = null;
                        if (context != null) {
                            System.err.println("logging out");
                            try {
                                context.logout();
                            } catch (LoginException ex) {
                                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            context = null;
                        }
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
        } catch (PrivilegedActionException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
