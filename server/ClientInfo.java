package server;

public class ClientInfo {
    private String ip;
    private int port;

    public ClientInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() { return ip; }
    public int getPort() { return port; }
}
