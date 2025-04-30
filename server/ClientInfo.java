package server;

/**
 * Represents information about a client or helper, including its IP address and port.
 */
public class ClientInfo {
    private String ip;
    private int port;

    /**
     * Constructs a ClientInfo instance with the given IP address and port.
     *
     * @param ip IP address of the client
     * @param port Port number on which the client is reachable
     */
    public ClientInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    /**
     * Returns the IP address of the client.
     *
     * @return the IP address
     */
    public String getIp() {
        return ip;
    }

    /**
     * Returns the port number of the client.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }
}
