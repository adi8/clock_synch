package client;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Client {
    private DatagramSocket clientSocket;

    private InetAddress serverAddress;

    private final static int SERVER_PORT = 4011;

    private final static int CLIENT_PORT = 4012;

    private final static String REPORT = "report.log";

    private final static String HISTO = "histo.txt";

    private final static int PACKET_TIMEOUT = 2;

    private final AtomicInteger seq;

    public static List<Integer> seqSent;

    public static List<Double> seqSentTime;

    public static List<Integer> seqRecv;

    public static List<Integer> seqDropped;

    public static List<Double> seqDroppedTime;

    public static List<Double> seqRTT;

    public static List<Double> seqTheta;

    public static List<Double> smoothedTheta;

    public static List<Double> drift;

    public static Map<Double, Integer> histoMap;

    public static int sentPackets;

    public static volatile boolean exitFlag;

    public static volatile Lock l;

    public static volatile Condition print;

    public static int expTime;

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
        histoMap = Collections.synchronizedMap(new HashMap<Double, Integer>());
        seqDroppedTime = Collections.synchronizedList(new ArrayList<>());
        drift = Collections.synchronizedList(new ArrayList<>());

        exitFlag = false;
        l = new ReentrantLock();
        print = l.newCondition();

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
            // Create packet to send
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

            // Send the packet
            clientSocket.send(p);

            // Increment sent packet count
            sentPackets++;
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
        double avgDrift = findAverage(drift);

        String report = String.format("Clock synch runt time (m)   : %d\n", Client.expTime) +
                        String.format("Number of packets sent      : %d\n", getSentPackets()) +
                        String.format("Number of packets received  : %d\n", seqRecv.size()) +
                        String.format("Number of packets dropped   : %d\n", seqDropped.size()) +
                        String.format("Percentage of packet drops  : %f\n", ((double)seqDropped.size())/getSentPackets()) +
                        String.format("Average round trip time (s) : %.6f\n", avgRTT) +
                        String.format("Average theta (s)           : %.6f\n", avgTheta) +
                        String.format("Average drift rate (s/s)    : %.9f\n", avgDrift);

        StringBuilder droppedReport = new StringBuilder();
        droppedReport.append("Dropped Packets: \n");
        for (int i = 0; i < seqDropped.size(); i++) {
            droppedReport.append(String.format("[%04d, %-8.6f]\n", seqDropped.get(i), seqDroppedTime.get(i)));
        }

        StringBuilder histoReport = new StringBuilder();
        histoReport.append("Histogram Report: \n");
        List<Double> sortedKeys = new ArrayList<>(histoMap.keySet());
        Collections.sort(sortedKeys);
        for (double key : sortedKeys) {
            int freq = Client.histoMap.get(key);
            histoReport.append(String.format("%9.6f : %s\n", key, freq));
        }

        try {
            System.out.println(report);
            System.out.println(droppedReport.toString());
            System.out.println(histoReport.toString());
            fw.write(report);
            fw.write(droppedReport.toString());
            fw.write(histoReport.toString());
            fw.flush();
            fw.close();
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    public void createHisto() {
        FileWriter fw = null;
        try {
            fw = new FileWriter(HISTO);
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
            return;
        }

        List<Double> sortedKeys = new ArrayList<>(histoMap.keySet());

        Collections.sort(sortedKeys);

        for (Double key : sortedKeys) {
            int freq = Client.histoMap.get(key);
            String stars = getStars(freq);
            String histoLine = String.format("%9.6f:%s\n", key, stars);
            try {
                fw.write(histoLine);
                fw.flush();
            }
            catch (IOException e) {
                System.out.println("ERROR: " + e.getMessage());
            }

        }

        try {
            fw.close();
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }

    }

    public String getStars(int num) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < num; i++) {
            stars.append('*');
        }
        return stars.toString();
    }

    public static void main(String[] args) {
        int mins = -1;
        if (args.length == 2) {
            try {
                mins = Integer.parseInt(args[1]);
                Client.expTime = mins;
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
                if (!exitFlag) {
                    client.send();
                }
            }
        }, 0, 10*1000);

        // Remove packets that have not returned within timeout
        Timer dropTimer = new Timer();
        dropTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                List<Integer> idxToRemove = new ArrayList<>();
                for(int i = 0; i < Client.seqSentTime.size(); i++) {
                    if (((Instant.now().toEpochMilli() / 1000d) - Client.seqSentTime.get(i)) >= PACKET_TIMEOUT) {
                        idxToRemove.add(i);
                    }
                }

                for (int i : idxToRemove) {
                    if (Client.seqSent.size() > 0) {
                        Client.seqDropped.add(Client.seqSent.get(i));
                        Client.seqDroppedTime.add(Client.seqSentTime.get(i));
                        Client.seqSentTime.remove(i);
                        Client.seqSent.remove(i);
                    }
                }
            }
        }, 1*1000, PACKET_TIMEOUT*1000);

        // End the program after 1 minute
        Timer exitTimer = new Timer();
        exitTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                exitFlag = true;

                Client.l.lock();

                try {
                    print.await();
                }
                catch (InterruptedException e) {
                    System.out.println("ERROR: " + e.getMessage());
                }

                client.printReport();
                client.createHisto();

                Client.l.unlock();

                System.exit(0);
            }
        }, mins*60*1000);

    }
}