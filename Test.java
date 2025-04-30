import java.io.*;
import java.util.*;

/**
 * Test class that launches a server and multiple clients as separate processes
 * to simulate concurrent downloads with delegation and logs each component's output.
 */
public class Test {

    /**
     * Main method to configure, launch, and coordinate server and client processes.
     *
     * @param args Command-line arguments to configure number of clients, DC, port, capacity, file name, etc.
     * @throws Exception if any I/O or process error occurs
     */
    public static void main(String[] args) throws Exception {
        int clientCount = 4;
        int dc = 2;
        double probability = 0.3;
        int port = 5000;
        int capacity = 2;
        String fileName = "file1.txt";
        String ip = "127.0.0.1";

        // Parse command-line arguments
        for (String arg : args) {
            if (arg.startsWith("--clients=")) {
                clientCount = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--DC=")) {
                dc = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--p=")) {
                probability = Double.parseDouble(arg.split("=")[1]);
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--capacity=")) {
                capacity = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--file=")) {
                fileName = arg.split("=")[1];
            } else if (arg.startsWith("--ip=")) {
                ip = arg.split("=")[1];
            }
        }

        String logDir = "./test_logs";
        new File(logDir).mkdirs();

        List<Process> processes = new ArrayList<>();

        // Launch the server process
        ProcessBuilder serverPb = new ProcessBuilder(
                "java", "server.Server", "--port=" + port, "--capacity=" + capacity, "--p=" + probability
        );
        serverPb.redirectOutput(new File(logDir + "/server.log"));
        serverPb.redirectError(new File(logDir + "/server_error.log"));
        processes.add(serverPb.start());

        Thread.sleep(5000); // Give the server time to initialize

        // Launch client processes
        for (int i = 0; i < clientCount; i++) {
            ProcessBuilder clientPb = new ProcessBuilder(
                    "java", "client.Client",
                    "--ip=" + ip,
                    "--port=" + port,
                    "--file=" + fileName,
                    "--DC=" + dc
            );
            clientPb.redirectOutput(new File(logDir + "/client_" + i + ".log"));
            clientPb.redirectError(new File(logDir + "/client_" + i + "_error.log"));
            processes.add(clientPb.start());
        }

        // Wait for all client processes to finish
        for (int i = 1; i < processes.size(); i++) {
            processes.get(i).waitFor();
        }

        // Stop the server
        processes.get(0).destroy();
        System.out.println("Tous les clients ont termine.");
    }
}
