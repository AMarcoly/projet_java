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

    public static void main(String[] args) {
        System.out.println("=== Starting Tests ===");

        testUtils();
        testServer();
        testClient();

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

    private static void testServer() {
        System.out.println(">> Testing Server");

        try {
            Thread serverThread = new Thread(() -> {
                Server server = new Server(5000, 2); // port 5000, capacité 2 connexions simultanées
                server.start();
            });

            serverThread.start();
            Thread.sleep(1000); // attendre que le serveur démarre correctement

            System.out.println("✅ Server started successfully (manual verification needed)");

        } catch (Exception e) {
            System.out.println("❌ Server start failed: " + e.getMessage());
        }

        System.out.println();
    }

    private static void testClient() {
        System.out.println(">> Testing Client");

        try {
            Thread clientThread = new Thread(() -> {
                Client client = new Client("127.0.0.1", 5000, "file1.txt", 2); // Teste la connexion
                client.connect();
            });

            clientThread.start();
            clientThread.join(); // attendre que le client termine

            System.out.println("✅ Client connected and operated successfully (manual verification needed)");

        } catch (Exception e) {
            System.out.println("❌ Client test failed: " + e.getMessage());
        }

        System.out.println();
    }

    private static void testServerAndClientEndToEnd() {
        System.out.println(">> Testing End-to-End Server-Client");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // 1. Lancer le serveur
            executor.submit(() -> {
                Server server = new Server(5000, 2); // port 5000, 2 connexions simultanées
                server.start();
            });

            Thread.sleep(1000); // attendre que le serveur soit bien démarré

            // 2. Lancer le client
            Future<Boolean> clientResult = executor.submit(() -> {
                Client client = new Client("127.0.0.1", 5000, "file1.txt", 2);
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
            executor.shutdownNow(); // Arrêter tous les threads (serveur + client)
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

    private static void cleanUp() {
        // Supprimer les fichiers de test après les tests
        File serverDir = new File("./server_files/");
        File clientDir = new File("./client_files/");

        if (serverDir.exists()) {
            for (File file : serverDir.listFiles()) {
                file.delete();
            }
        }

        if (clientDir.exists()) {
            for (File file : clientDir.listFiles()) {
                file.delete();
            }
        }
        System.out.println(">> Cleaned up test files");
    }
}
