package server;

import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Enumeration;

public class Server {

    private DatagramSocket serverSocket;

    private final static int SERVER_PORT = 4011;

    private final static int CLIENT_PORT = 4012;

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.S");

    public static int packetRecvd = 0;

    public Server() {
        try {
            this.serverSocket = new DatagramSocket(SERVER_PORT);
        }
        catch (SocketException e) {
            System.out.println("ERROR: Socket error. " + e.getMessage());
            System.exit(1);
        }
    }

    public static String getAddress() {
        String ipAddress = "";
        try {
            for (final Enumeration<NetworkInterface> interfaces
                 = NetworkInterface.getNetworkInterfaces();
                 interfaces.hasMoreElements();)
            {
                final NetworkInterface cur = interfaces.nextElement();

                if ( cur.isLoopback() )
                    continue;

                if (!(cur.getDisplayName().startsWith("w") || cur.getDisplayName().startsWith("e")))
                    continue;

                for (final InterfaceAddress addr : cur.getInterfaceAddresses()) {
                    final InetAddress inetAddr = addr.getAddress();

                    if (!(inetAddr instanceof Inet4Address))
                        continue;

                    ipAddress += inetAddr.getHostAddress() + " ";
                }

            }
        }
        catch (Exception e) {
            System.out.println("Failed: " + e.getMessage());
            e.printStackTrace();
        }

        return ipAddress;
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

            String currentStat = String.format("%d\t\t %f\t\t %s",
                   ++packetRecvd,
                   currSecondsSinceEpoch,
                    sdf.format(new Date((long)(currSecondsSinceEpoch * 1000d))));

            System.out.println(currentStat);

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
        System.out.println("IP Address: " + getAddress());
        String header = "Packet\t Current Time (ms since epoch)\t Current Time\n" +
                        "---------------------------------------------------------------------";
        System.out.println(header);
        server.listen();
    }
}
