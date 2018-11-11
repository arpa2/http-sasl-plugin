/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mansoft.sasl;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author hfman
 */
public class TestClient {
    private static PrintStream stdIn;
    private static Thread serverStartThread;

    //@BeforeClass
    public static void setUp() throws Exception {
        PipedOutputStream stdInOutputStream = new PipedOutputStream();
        stdIn = new PrintStream(stdInOutputStream);
        System.setIn(new PipedInputStream(stdInOutputStream));
        serverStartThread = new Thread(new Runnable() {

            @Override
            public void run() {
                String[] args = {
                };
                nl.mansoft.sasl.Client.main(args);
            }
        });
        serverStartThread.start();
    }

    public void writeInt32(int length) throws IOException {
        stdIn.write(length);
        stdIn.write(length >> 8);
        stdIn.write(length >> 16);
        stdIn.write(length >> 24);
    }

    private void processResponse(CloseableHttpResponse response) throws IOException {
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the user MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        try {
            System.out.println(response.getStatusLine());
            Header[] headers = response.getHeaders("WWW-Authenticate");

            for (Header header : headers) {
                String wwwAuthenticate = header.getValue();
                System.out.println(wwwAuthenticate);
                Map<String, String> map = SaslParser.parse(wwwAuthenticate);
                System.out.println(map);
                JsonBuilderFactory factory = Json.createBuilderFactory(null);
                JsonObjectBuilder outputJsonBuilder = factory.createObjectBuilder();
                if (map != null) {
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        outputJsonBuilder.add(entry.getKey(), entry.getValue());
                    }
                }
                JsonObject outputJson = outputJsonBuilder.build();
                String output = outputJson.toString();
                System.out.println(output);
                writeInt32(output.length());
                stdIn.print(output);
            }
            HttpEntity entity1 = response.getEntity();
            // do something useful with the response body
            //System.out.println(EntityUtils.toString(entity1));
            // and ensure it is fully consumed
            EntityUtils.consume(entity1);
        } finally {
            response.close();
        }
    }

    //@Test
    public void testClient() throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet("http://mansoft.nl:8080/HttpSasl/SaslServlet");
        CloseableHttpResponse response1 = httpclient.execute(httpGet);
        processResponse(response1);
        Header authorization = new BasicHeader("Authorization", "SASL mech=\"DIGEST-MD5\",realm=\"test-realm.nl\"");
        httpGet.addHeader(authorization);
        CloseableHttpResponse response2 = httpclient.execute(httpGet);
        processResponse(response2);
        try {
            Thread.sleep(100000);
        } catch (InterruptedException ex) {
            Logger.getLogger(TestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
