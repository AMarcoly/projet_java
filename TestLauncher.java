import server.Server;
import client.Client;
import common.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestLauncher {

    private static final int PORT_END_TO_END = 5000;
    private static final int PORT_DELEGATION = 5001;
    private static final String TEST_FILE = "file1.txt";
    private static final int DEFAULT_DC = 2;
    private static final int DEFAULT_CS = 2;

    public static void main(String[] args) {
        System.out.println("=== Starting Tests ===");

        testUtils();
        testServerAndClientEndToEnd();
        testDelegationScenario();

        System.out.println("=== Tests Finished ===");
    }

    private static void testUtils() {
        System.out.println(">> Testing Utils");
        
        byte[] example = { (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF };
        String expectedHex = "deadbeef";
        String resultHex = Utils.bytesToHex(example);

        System.out.println("[bytesToHex] Expected: " + expectedHex + " - Result: " + resultHex);
        System.out.println(resultHex.equals(expectedHex) ? "✅ PASS" : "❌ FAIL");

        String message = "Hello, world!";
        byte[] data = message.getBytes();
        String expectedMd5 = "6cd3556deb0da54bca060b4c39479839";
        String resultMd5 = Utils.computeMD5(data);

        System.out.println("[computeMD5] Expected: " + expectedMd5 + " - Result: " + resultMd5);
        System.out.println(resultMd5.equals(expectedMd5) ? "✅ PASS" : "❌ FAIL");

        System.out.println();
    }

    private static void testServerAndClientEndToEnd() {
        System.out.println(">> Testing End-to-End Server-Client");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Start server with parameters
            executor.submit(() -> {
                new Server(new String[]{
                    "--port=" + PORT_END_TO_END,
                    "--cs=" + DEFAULT_CS
                }).start();
            });

            Thread.sleep(1500); // Wait for server to start

            // Start client with parameters
            Future<Boolean> clientResult = executor.submit(() -> {
                new Client(new String[]{
                    "--server=127.0.0.1:" + PORT_END_TO_END,
                    "--file=" + TEST_FILE,
                    "--dc=" + DEFAULT_DC
                }).connect();
                return verifyDownload(TEST_FILE);
            });

            boolean success = clientResult.get(5, TimeUnit.MINUTES);

            if (success) {
                System.out.println("✅ End-to-End test passed: MD5 matches");
            } else {
                System.out.println("❌ End-to-End test failed: MD5 does not match");
            }

        } catch (Exception e) {
            System.out.println("❌ End-to-End test error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
        }

        System.out.println();
    }

    private static void testDelegationScenario() {
        System.out.println(">> Testing Delegation Scenario");

        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            // Start server with parameters
            executor.submit(() -> {
                new Server(new String[]{
                    "--port=" + PORT_DELEGATION,
                    "--cs=" + DEFAULT_CS
                }).start();
            });

            Thread.sleep(2000); // Wait for server to start

            // Start helper client
            executor.submit(() -> {
                new Client(new String[]{
                    "--server=127.0.0.1:" + PORT_DELEGATION,
                    "--file=" + TEST_FILE,
                    "--dc=" + DEFAULT_DC
                }).connect();
            });

            Thread.sleep(3000); // Wait for helper to register

            // Start first regular client
            Future<Boolean> client1Result = executor.submit(() -> {
                Thread.sleep(500);
                new Client(new String[]{
                    "--server=127.0.0.1:" + PORT_DELEGATION,
                    "--file=" + TEST_FILE,
                    "--dc=" + DEFAULT_DC
                }).connect();
                return verifyDownload(TEST_FILE);
            });

            // Start second regular client with delay
            Future<Boolean> client2Result = executor.submit(() -> {
                Thread.sleep(1000);
                new Client(new String[]{
                    "--server=127.0.0.1:" + PORT_DELEGATION,
                    "--file=" + TEST_FILE,
                    "--dc=" + DEFAULT_DC
                }).connect();
                return verifyDownload(TEST_FILE);
            });

            boolean success1 = client1Result.get(5, TimeUnit.MINUTES);
            boolean success2 = client2Result.get(5, TimeUnit.MINUTES);

            if (success1 && success2) {
                System.out.println("✅ Delegation Test Passed: All clients received correct files");
            } else {
                System.out.println("❌ Delegation Test Failed: At least one client has incorrect file");
            }

        } catch (Exception e) {
            System.out.println("❌ Delegation Test Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
        }

        System.out.println();

        try {
            String metrics = Files.readString(Paths.get("./logs/server_metrics.log"));
            System.out.println("\n=== METRICS ===");
            System.out.println(metrics);
        } catch (IOException e) {
            System.out.println("Failed to read metrics: " + e.getMessage());
        }
        
    }

    private static boolean verifyDownload(String fileName) {
        try {
            Path serverPath = Paths.get("./server_files/", fileName);
            Path clientPath = Paths.get("./client_files/", fileName);
            
            if (!Files.exists(serverPath)) {
                System.out.println("❌ Server file missing");
                return false;
            }
            if (!Files.exists(clientPath)) {
                System.out.println("❌ Client file missing");
                return false;
            }

            long serverSize = Files.size(serverPath);
            long clientSize = Files.size(clientPath);
            
            if (serverSize != clientSize) {
                System.out.println("❌ Size mismatch: Server=" + serverSize + " Client=" + clientSize);
                return false;
            }

            String serverMd5 = Utils.computeMD5(Files.readAllBytes(serverPath));
            String clientMd5 = Utils.computeMD5(Files.readAllBytes(clientPath));
            
            System.out.println("[MD5] Server: " + serverMd5);
            System.out.println("[MD5] Client: " + clientMd5);
            
            return serverMd5.equals(clientMd5);
        } catch (Exception e) {
            System.out.println(" Verification failed: " + e.getMessage());
            return false;
        }
    }

   
}