package client;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Client {
    private DatagramSocket clientSocket;

    private InetAddress serverAddress;

    private final static int SERVER_PORT = 4011;

    private final static int CLIENT_PORT = 4012;

    private final static String REPORT = "report.log";

    private final AtomicInteger seq;

    public static List<Integer> seqSent;

    public static List<Double> seqSentTime;

    public static List<Integer> seqRecv;

    public static List<Integer> seqDropped;

    public static List<Double> seqRTT;

    public static List<Double> seqTheta;

    public static List<Double> smoothedTheta;

    public static int sentPackets;

    public Client(String serverAddr) {
        try {
            this.serverAddress = InetAddress.getByName(serverAddr);
        }
        catch (UnknownHostException e) {
            System.out.println("ERROR: Unknown host. " + e.getMessage());
            System.out.println("Usage: ./client <server-address> [mins]");
            System.exit(1);
        }

        this.seq = new AtomicInteger();
        seqSent = Collections.synchronizedList(new ArrayList<>());
        seqSentTime = Collections.synchronizedList(new ArrayList<>());
        seqRecv = Collections.synchronizedList(new ArrayList<>());
        seqDropped = Collections.synchronizedList(new ArrayList<>());
        seqRTT = Collections.synchronizedList(new ArrayList<>());
        seqTheta = Collections.synchronizedList(new ArrayList<>());
        smoothedTheta = Collections.synchronizedList(new ArrayList<>());

        sentPackets = 0;

        try {
            clientSocket = new DatagramSocket(CLIENT_PORT);
        }
        catch (SocketException e) {
            System.out.println("ERROR: Socket exception. " + e.getMessage());
            System.exit(1);
        }
    }

    public DatagramSocket getSocket() {
        return this.clientSocket;
    }

    public void send() {
        try {
            int sequenceNo = this.seq.incrementAndGet();
            double currSecondsSinceEpoch = Instant.now().toEpochMilli() / 1000d;
            String synchMsg = sequenceNo + " " + String.format("%.6f", currSecondsSinceEpoch);

            DatagramPacket p = new DatagramPacket(synchMsg.getBytes(),
                    synchMsg.getBytes().length,
                    serverAddress,
                    SERVER_PORT);

            // Add sequence to seqSent
            seqSent.add(sequenceNo);
            seqSentTime.add(currSecondsSinceEpoch);

            // Increment sent packet count
            sentPackets++;

            // Send the packet
            clientSocket.send(p);
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    public int getSentPackets() {
        return sentPackets;
    }

    public double findAverage(List<Double> data) {
        double sum = 0;
        for (double val : data) {
            sum += val;
        }

        return sum / data.size();
    }

    public void printReport() {
        FileWriter fw = null;
        try {
            fw = new FileWriter(REPORT);
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
            return;
        }

        double avgRTT = findAverage(seqRTT);
        double avgTheta = findAverage(seqTheta);

        String report = String.format("Number of packets sent     : %d\n", getSentPackets()) +
                        String.format("Number of packets received : %d\n", seqRecv.size()) +
                        String.format("Number of packets dropped  : %d\n", seqDropped.size()) +
                        String.format("Percentage of packet drops : %f\n", ((double)seqDropped.size())/getSentPackets()) +
                        String.format("Average round trip time    : %.2f\n", avgRTT) +
                        String.format("Average theta              : %.2f\n", avgTheta);

        try {
            System.out.println(report);
            fw.write(report);
            fw.flush();
            fw.close();
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int mins = -1;
        if (args.length == 2) {
            try {
                mins = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException e) {
                System.out.println("ERROR: " + e.getMessage());
                System.exit(1);
            }
        }
        else if (args.length != 1) {
            System.out.println("Usage: ./client <server-address> [mins]");
            System.exit(1);
        }
        final Client client = new Client(args[0]);

        if (mins == -1) {
            InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isr);
            System.out.println("Enter number of mins to run");
            try {
                mins = Integer.parseInt(br.readLine());
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("UDP Client Started...");

        // Start receiver thread
        ClientReceiver clientReceiver = new ClientReceiver(client.getSocket());
        clientReceiver.start();

        // Send packet every 10 seconds
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                client.send();
            }
        }, 0, 10*1000);

        // Remove packets that have not returned within timeout
        Timer dropTimer = new Timer();
        dropTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                List<Integer> idxToRemove = new ArrayList<>();
                for(int i = 0; i < Client.seqSentTime.size(); i++) {
                    if (Client.seqSentTime.get(i) - (Instant.now().toEpochMilli() / 1000d) >= 15d) {
                        idxToRemove.add(i);
                    }
                }

                for (int i : idxToRemove) {
                    Client.seqSentTime.remove(i);
                    Client.seqSent.remove(i);
                }
            }
        }, 15*1000, 3*1000);

        // End the program after 1 minute
        Timer exitTimer = new Timer();
        exitTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                client.printReport();
                System.exit(0);
            }
        }, mins*60*1000);

    }
}