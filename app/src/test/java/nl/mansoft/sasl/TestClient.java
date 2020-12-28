/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mansoft.sasl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author hfman
 */
public class TestClient {
    private PrintStream stdIn;
    private InputStream stdOut;
    private Thread serverStartThread;

    @Before
    public void setUp() throws Exception {
        System.err.println("setUp");
        PipedOutputStream stdInOutputStream = new PipedOutputStream();
        stdIn = new PrintStream(stdInOutputStream);
        System.setIn(new PipedInputStream(stdInOutputStream));
        PipedInputStream StdOutInputStream = new PipedInputStream();
        stdOut = StdOutInputStream;
        System.setOut(new PrintStream(new PipedOutputStream(StdOutInputStream)));

        serverStartThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String[] args = {
                };
                Client.main(args);
            }
        });
        serverStartThread.start();

//        Client.main(new String[] { });
    }

    public int readInt32() throws IOException {
        int byte1 = stdOut.read();
        if (byte1 == -1) {
            return -1;
        }
        int byte2 = stdOut.read() << 8;
        if (byte2 == -1) {
            return -1;
        }
        int byte3 = stdOut.read() << 16;
        if (byte3 == -1) {
            return -1;
        }
        int byte4 = stdOut.read() << 24;
        if (byte4 == -1) {
            return -1;
        }
        return byte1 | byte2 | byte3 | byte4;
    }

    public void writeInt32(int length) throws IOException {
        stdIn.write(length);
        stdIn.write(length >> 8);
        stdIn.write(length >> 16);
        stdIn.write(length >> 24);
    }

    private static String addField(JsonObject inputJson, String separator, String name, String quote) {
        String value = Client.jsonGetString(inputJson, name);
        return value == null ? "" : separator + name + "=" + quote + value + quote;
    }

    private static void printBase64Value(JsonObject inputJson, String name) {
        String base64 = Client.jsonGetString(inputJson, name);
        if (base64 != null) {
            byte[] response = Base64.getDecoder().decode(base64);
            System.err.println(name + ": " + new String(response));
        }
    }
    private String processResponse(CloseableHttpResponse response) throws IOException {
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the user MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        String authorization = "";
        try {
            StatusLine statusLine = response.getStatusLine();
            System.err.println("Statusline: " + statusLine);
            int statusCode = statusLine.getStatusCode();
            switch (statusCode) {
                case 200:
                    HttpEntity entity1 = response.getEntity();
                    // do something useful with the response body
                    System.err.println(EntityUtils.toString(entity1));
                    // and ensure it is fully consumed
                    EntityUtils.consume(entity1);
                    break;
                case 401:
                    Header[] headers = response.getHeaders("WWW-Authenticate");

                    for (Header header : headers) {
                        String wwwAuthenticate = header.getValue();
                        System.err.println("WWW-Authenticate: " + wwwAuthenticate);
                        Map<String, String> map = SaslParser.parse(wwwAuthenticate);
                        //System.err.println(map);
                        JsonBuilderFactory factory = Json.createBuilderFactory(null);
                        JsonObjectBuilder outputJsonBuilder = factory.createObjectBuilder();
                        if (map != null) {
                            for (Map.Entry<String, String> entry : map.entrySet()) {
                                outputJsonBuilder.add(entry.getKey(), entry.getValue());
                            }
                        }
                        JsonObject outputJson = outputJsonBuilder.build();
                        printBase64Value(outputJson, "s2c");
                        String output = outputJson.toString();
                        System.err.println("JSON sent to client: " + output);
                        writeInt32(output.length());
                        stdIn.print(output);
                        stdIn.flush();
                        int len = readInt32();
                        byte[] message = new byte[len];
                        stdOut.read(message);
                        String jsonResponse = new String(message, "UTF-8");
                        System.err.println("JSON received from client: " + jsonResponse);
                        StringReader reader = new StringReader(jsonResponse);
                        JsonParser parser = Json.createParser(reader);
                        parser.next();
                        JsonObject inputJson = parser.getObject();
                        printBase64Value(inputJson, "c2s");
                        authorization =
                            "SASL" +
                            addField(inputJson,  " ", "mech", "\"") +
                            addField(inputJson,  ",", "realm", "\"") +
                            addField(inputJson,  ",", "s2s", "\"") +
                            addField(inputJson,  ",", "c2s", "\"");
                    }
                    break;
            }
        } finally {
            response.close();
        }
        return authorization;
    }

    private void saslTest(String url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String authorization = null;
        while (authorization == null || !authorization.isEmpty()) {
            HttpGet httpGet = new HttpGet(url);
            if (authorization != null) {
                Header authorizationHeader = new BasicHeader("Authorization", authorization);
                httpGet.addHeader(authorizationHeader);
            }
            CloseableHttpResponse response = httpclient.execute(httpGet);
            authorization = processResponse(response);
            System.err.println("Authorization: " + authorization);
        }
        stdIn.close();
        stdOut.close();
        try {
            serverStartThread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(TestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void testLocal() throws IOException {
        saslTest("http://localhost:8080/HttpSasl/SaslServlet");
    }

/*
    @Test
    public void testMansoft() throws IOException {
        //saslTest("http://mansoft.nl:8080/HttpSasl/SaslServlet");
    }
*/
}
