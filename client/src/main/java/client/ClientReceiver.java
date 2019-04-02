package client;

import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class ClientReceiver extends Thread {
    private DatagramSocket clientSocket;

    private final static String LOG_FILE = "log.txt";

    public ClientReceiver(DatagramSocket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        String msg;

        // Print headers for report
        try {
            FileWriter fw = new FileWriter(LOG_FILE);
            String header = String.format("%-10s %-10s %-10s %-10s %-21s %-21s\n", "Packet", "RTT", "θ", "Smoothed θ", "Current", "Corrected");

            // Print to console
            System.out.println(header);
            System.out.println("----------------------------------------------------------------------------------------");

            // Print to file
            fw.write(header);
            fw.write("----------------------------------------------------------------------------------------\n");
            fw.flush();
            fw.close();
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        while (true) {
            byte[] buf = new byte[1024];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            double t0 = 0d;

            try {
                clientSocket.receive(p);
                t0 = Instant.now().toEpochMilli() / 1000d;
            }
            catch(IOException e) {
                System.out.println("ERROR: " + e.getMessage());
                continue;
            }

            msg = new String(p.getData()).trim();
            String[] msgParts = msg.split(" ");

            int seq = Integer.parseInt(msgParts[0]);
            if (Client.seqSent.contains(seq)) {
                // Remove from seqSent and its corresponding time
                Client.seqSent.remove(new Integer(seq));

                double t3 = Double.parseDouble(msgParts[1]);
                double t2 = Double.parseDouble(msgParts[2]);
                double t1 = Double.parseDouble(msgParts[3]);
                processTime(seq, t3, t2, t1, t0);
            }
            else {
                Client.seqDropped.add(seq);
                System.out.format("%-10d %-10s %-10s %-10s %-21s %-21s\n", seq, "-", "-", "-", "-", "-");
            }
        }
    }

    public void processTime(int seq, double t3, double t2, double t1, double t0) {
        double rtt = (t2 - t3) + (t0 - t1);
        double theta = ((t2 - t3) - (t0 - t1)) / 2d;
        Client.seqRecv.add(seq);
        Client.seqRTT.add(rtt);
        Client.seqTheta.add(theta);

        List<Double> tmpRTT = Client.seqRTT.subList(Math.max(Client.seqRTT.size() - 8, 0), Client.seqRTT.size());
        List<Double> tmpTheta = Client.seqTheta.subList(Math.max(Client.seqTheta.size() - 8, 0), Client.seqTheta.size());

        int idx = findMinIDX(tmpRTT);
        double smoothedTheta = tmpTheta.get(idx);
        double currTime = Instant.now().toEpochMilli() / 1000d;
        double correctedTime = currTime + smoothedTheta;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        String currTimeStr = sdf.format(new Date((long)(currTime * 1000d)));
        String correctTimeStr = sdf.format(new Date((long)(correctedTime * 1000d)));

        String record = String.format("%-10d %-10.6f %-10.6f %-10.6f %-21s %-21s\n",
                seq,
                rtt,
                theta,
                smoothedTheta,
                currTimeStr,
                correctTimeStr);

        try {
            FileWriter fw = new FileWriter(LOG_FILE, true);

            System.out.println(record);

            fw.write(record);
            fw.flush();
            fw.close();
        }
        catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }

    }

    public int findMinIDX(List<Double> tmp) {
        int i;
        int idx = -1;
        double min = 9999;
        double val = 0;
        for(i = 0; i < tmp.size(); i++) {
            val = tmp.get(i);
            if (val < min) {
                min = val;
                idx = i;
            }
        }

        return idx;
    }
}
