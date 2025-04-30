import server.Server;
import client.Client;
import common.LoggerUtil;
import common.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class TestLauncher {

    private static final Logger logger = LoggerUtil.getLogger(TestLauncher.class);
    private static final int PORT = 5000;
    private static final int CAPACITY = 4;

    public static void main(String[] args) {
        logger.info("=== Starting Tests ===");

        Server server = new Server(PORT, CAPACITY);
        Thread serverThread = new Thread(server::start);
        serverThread.start();

        try {
            Thread.sleep(1000); // Laisser le serveur démarrer
            testUtils();
            testServerAndClientEndToEnd();
            testDelegationScenario();
        } catch (InterruptedException e) {
            logger.severe("Main thread interrupted: " + e.getMessage());
        } finally {
            server.stop();
            try {
                serverThread.join(3000);
            } catch (InterruptedException ignored) {}
            logger.info("=== Tests Finished ===");
            System.exit(0);
        }
    }

    private static void testUtils() {
        logger.info(">> Testing Utils");

        byte[] example = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        String expectedHex = "deadbeef";
        String resultHex = Utils.bytesToHex(example);
        logger.info("[bytesToHex] Expected: " + expectedHex + " - Result: " + resultHex);
        logger.info(resultHex.equals(expectedHex) ? "✅ PASS" : "❌ FAIL");

        String message = "Hello, world!";
        byte[] data = message.getBytes();
        String expectedMd5 = "6cd3556deb0da54bca060b4c39479839";
        String resultMd5 = Utils.computeMD5(data);
        logger.info("[computeMD5] Expected: " + expectedMd5 + " - Result: " + resultMd5);
        logger.info(resultMd5.equals(expectedMd5) ? "✅ PASS" : "❌ FAIL");

        logger.info("");
    }

    private static void testServerAndClientEndToEnd() {
        logger.info(">> Testing End-to-End Server-Client");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> clientResult = executor.submit(() -> {
                Client client = new Client("127.0.0.1", PORT, "file1.txt", 2);
                client.connect();
                return verifyDownload("file1.txt");
            });

            boolean success = clientResult.get(5, TimeUnit.MINUTES);

            if (success) {
                logger.info("✅ End-to-End test passed: MD5 matches");
            } else {
                logger.warning("❌ End-to-End test failed: MD5 does not match");
            }

        } catch (Exception e) {
            logger.severe("❌ End-to-End test error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        logger.info("");
    }

    private static void testDelegationScenario() throws InterruptedException {
        logger.info(">> Testing Delegation Scenario");

        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            // 1. Client helper
            executor.submit(() -> {
                Client helper = new Client("127.0.0.1", PORT, "file1.txt", 2);
                helper.connect();
            });

            Thread.sleep(3000); // Laisser le helper s'enregistrer

            // 2. Deux autres clients
            Future<Boolean> client1Result = executor.submit(() -> {
                Client client1 = new Client("127.0.0.1", PORT, "file1.txt", 2);
                client1.connect();
                return verifyDownload("file1.txt");
            });

            Future<Boolean> client2Result = executor.submit(() -> {
                Client client2 = new Client("127.0.0.1", PORT, "file1.txt", 2);
                client2.connect();
                return verifyDownload("file1.txt");
            });

            boolean success1 = client1Result.get(5, TimeUnit.MINUTES);
            boolean success2 = client2Result.get(5, TimeUnit.MINUTES);

            if (success1 && success2) {
                logger.info("✅ Delegation Test Passed: All clients received correct files");
            } else {
                logger.warning("❌ Delegation Test Failed: At least one client has incorrect file");
            }

        } catch (Exception e) {
            logger.severe("❌ Delegation Test Error: " + e.getMessage());
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        logger.info("");
    }

    private static boolean verifyDownload(String fileName) {
        return verifyDownload(fileName, "client_files");
    }

    private static boolean verifyDownload(String fileName, String clientFolder) {
        try {
            File serverFile = new File("./server_files/" + fileName);
            File clientFile = new File("./" + clientFolder + "/" + fileName);

            if (!serverFile.exists() || !clientFile.exists()) {
                logger.warning("❌ Server or client file missing for comparison");
                return false;
            }

            byte[] serverData = Files.readAllBytes(serverFile.toPath());
            byte[] clientData = Files.readAllBytes(clientFile.toPath());

            String serverMd5 = Utils.computeMD5(serverData);
            String clientMd5 = Utils.computeMD5(clientData);

            logger.info("[MD5 Check] Server MD5: " + serverMd5);
            logger.info("[MD5 Check] Client MD5: " + clientMd5);

            return serverMd5.equals(clientMd5);
        } catch (IOException e) {
            logger.severe("❌ Error during MD5 verification: " + e.getMessage());
            return false;
        }
    }
}