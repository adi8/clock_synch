package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.Instant;

public class Server {

    private DatagramSocket serverSocket;

    private final static int SERVER_PORT = 4011;

    private final static int CLIENT_PORT = 4012;

    public Server() {
        try {
            this.serverSocket = new DatagramSocket(SERVER_PORT);
        }
        catch (SocketException e) {
            System.out.println("ERROR: Socket error. " + e.getMessage());
            System.exit(1);
        }

        // TODO: Print the servers IP address(es)
    }

    private void listen() {
        String msg;

        while (true) {
            byte[] buf = new byte[1024];
            DatagramPacket p = new DatagramPacket(buf, buf.length);

            try {
                serverSocket.receive(p);
            }
            catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                continue;
            }

            msg = new String(p.getData()).trim();
            double receiveSecondsSinceEpoch = Instant.now().toEpochMilli() / 1000d;
            String synchReponseMsg = msg + " " + String.format("%.6f", receiveSecondsSinceEpoch);
            double currSecondsSinceEpoch = Instant.now().toEpochMilli() / 1000d;
            synchReponseMsg += " " + String.format("%.6f", currSecondsSinceEpoch);

            DatagramPacket resp = new DatagramPacket(synchReponseMsg.getBytes(),
                                                     synchReponseMsg.getBytes().length,
                                                     p.getAddress(),
                                                     CLIENT_PORT);

            try {
                this.serverSocket.send(resp);
            }
            catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        System.out.println("UDP server started...");
        server.listen();
    }
}
