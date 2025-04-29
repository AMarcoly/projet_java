import server.Server;
import client.Client;
import common.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TestLauncher {

    private static final int PORT_END_TO_END = 5000;
    private static final int PORT_DELEGATION = 5001;

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
            executor.submit(() -> {
                Server server = new Server(PORT_END_TO_END, 2);
                server.start();
            });

            Thread.sleep(1000);

            Future<Boolean> clientResult = executor.submit(() -> {
                Client client = new Client("127.0.0.1", PORT_END_TO_END, "file1.txt", 2);
                client.connect();
                return verifyDownload("file1.txt");
            });

            boolean success = clientResult.get(5, TimeUnit.MINUTES);

            if (success) {
                System.out.println("✅ End-to-End test passed: MD5 matches");
            } else {
                System.out.println("❌ End-to-End test failed: MD5 does not match");
            }

        } catch (Exception e) {
            System.out.println("❌ End-to-End test error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }

        System.out.println();
    }

    private static void testDelegationScenario() {
        System.out.println(">> Testing Delegation Scenario");

        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            executor.submit(() -> {
                Server server = new Server(PORT_DELEGATION, 2); // capacityCs = 2 ici
                server.start();
            });

            Thread.sleep(1000);

            executor.submit(() -> {
                Client clientHelper = new Client("127.0.0.1", PORT_DELEGATION, "file1.txt", 2);
                clientHelper.connect();
            });

            Thread.sleep(3000);

            Future<Boolean> client1Result = executor.submit(() -> {
                Client client1 = new Client("127.0.0.1", PORT_DELEGATION, "file1.txt", 2);
                client1.connect();
                return verifyDownload("file1.txt");
            });

            Future<Boolean> client2Result = executor.submit(() -> {
                Client client2 = new Client("127.0.0.1", PORT_DELEGATION, "file1.txt", 2);
                client2.connect();
                return verifyDownload("file1.txt");
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
        } finally {
            executor.shutdownNow();
        }

        System.out.println();
    }

    private static boolean verifyDownload(String fileName) {
        try {
            File serverFile = new File("./server_files/" + fileName);
            File clientFile = new File("./client_files/" + fileName);

            if (!serverFile.exists() || !clientFile.exists()) {
                System.out.println("❌ Server or client file missing for comparison");
                return false;
            }

            byte[] serverData = Files.readAllBytes(serverFile.toPath());
            byte[] clientData = Files.readAllBytes(clientFile.toPath());

            String serverMd5 = Utils.computeMD5(serverData);
            String clientMd5 = Utils.computeMD5(clientData);

            System.out.println("[MD5 Check] Server MD5: " + serverMd5);
            System.out.println("[MD5 Check] Client MD5: " + clientMd5);

            return serverMd5.equals(clientMd5);
        } catch (IOException e) {
            System.out.println("❌ Error during MD5 verification: " + e.getMessage());
            return false;
        }
    }
}
    //     int randomIndex = (int) (Math.random() * connections.size());
    //     Socket connection = connections.get(randomIndex);
    //     try {
    //         connection.close();
    //         connections.remove(randomIndex);
    //         logger.info("Closed random connection: " + connection.getInetAddress());
    //     } catch (IOException e) {
    //         logger.warning("Error closing connection: " + e.getMessage());
    //     }
    // }