import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private final int WindowSize;
    private final double RxmtInterval;
    private final int LimitSeqNo;

    // Statistic Variables
    private int originPktNum;
    private int retransPktNum;
    private int deliveredPktNum;
    private int ackPktNum;
    private int corruptPktNum;

    // Calculating RTT variables
    ArrayList<Double> RTTList;
    private final HashMap<Integer, Double> sendTime;

    // Calculating communication time variables
    ArrayList<Double> CTList;
    private final HashMap<Integer, Double> oriSendTime;


    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // Variables for the sender (A)
    // Stores the data that is not sent yet.
    private Queue<Packet> unsentBuffer_a;
    // Stores the data that is sent but might require resent.
    private ArrayList<Packet> resentBuffer_a;
    private ArrayList<Integer> ack_buffer_a;
    private int nextSeqNum_a;
    private int lastAckNum_a;
    private boolean timerFlag_a;

    // Variables for the receiver (B)
    private ArrayList<Integer> buffer_SACK;
    private HashMap<Integer, Packet> seq_to_packet;
    //window notation
    private int wanted_B;

    private int[] SACK_B;


    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize + Packet.SACKSIZE + 1; // set appropriately; assumes SR here!
        RxmtInterval = delay;

        originPktNum = 0;
        retransPktNum = 0;
        deliveredPktNum = 0;
        ackPktNum = 0;
        corruptPktNum = 0;

        RTTList = new ArrayList<Double>();
        CTList = new ArrayList<Double>();
        sendTime = new HashMap<Integer, Double>();
        oriSendTime = new HashMap<Integer, Double>();
    }

    // This function copies the head packet from the queue,
    // sent it and update relevant values.
    protected void sendPacket() {
        if (unsentBuffer_a.isEmpty()) {
            return;
        }
        System.out.println("Packet sent by A with seq number " + unsentBuffer_a.peek().getSeqnum() + ", payload: " + unsentBuffer_a.peek().getPayload());
        toLayer3(0, unsentBuffer_a.peek());

        // Start timer
        if (!timerFlag_a) {
            startTimer(0, RxmtInterval);
            timerFlag_a = true;
        }
        // Record the sent time to calculate RTT & communication time
        sendTime.put(unsentBuffer_a.peek().getSeqnum(), getTime());
        oriSendTime.put(unsentBuffer_a.peek().getSeqnum(), getTime());
        resentBuffer_a.add(unsentBuffer_a.poll());
        originPktNum += 1;
    }

    protected void resendPacket() {
        if (resentBuffer_a.isEmpty()) {
            return;
        }

        if (!timerFlag_a) {
            startTimer(0, RxmtInterval);
            timerFlag_a = true;
        }

        // If the resentbuffer(outstanding packets) has something not sacked from B, send it again.
        for (int i = 0; i < resentBuffer_a.size(); i++) {
            if (!ack_buffer_a.contains(resentBuffer_a.get(i).getSeqnum())) {
                System.out.println("Packet sent by A with seq number " + resentBuffer_a.get(i).getSeqnum() + ", payload: " + resentBuffer_a.get(i).getPayload());
                toLayer3(0, resentBuffer_a.get(i));
                // Remove the record for RTT since the pkt is retransmitted.
                sendTime.remove(resentBuffer_a.get(i).getSeqnum());
                retransPktNum += 1;
            }
        }
    }


    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        System.out.println("Message received at A: " + message.getData());
        // Generate packet
        Packet pkt = new Packet(nextSeqNum_a, -1, message.getData().hashCode(), message.getData());
        unsentBuffer_a.add(pkt);

        // Check if the packet can be sent right away.
        if (resentBuffer_a.size() < WindowSize) {
            sendPacket();
        }
        // Increase the next sequence number.
        nextSeqNum_a += 1;
        if (nextSeqNum_a == LimitSeqNo) {
            nextSeqNum_a = 0;
        }
    }

    /**
     * printout the resentbuffer for debug
     */
    protected void printresent(Queue<Packet> q) {
        Queue<Packet> cool = new LinkedList<>(q);
        System.out.print("This is outside things: [");
        while (!cool.isEmpty()) {
            System.out.print(cool.poll().getSeqnum());
            System.out.print(", ");
        }
        System.out.println("]");
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {

        int originAckNum = packet.getAcknum();
        int ackNum = originAckNum;
        int[] temp = packet.getsack();

        // Check if package is corrupted.
        if (!(packet.getSeqnum() == -1 && packet.getPayload().equals("") && ackNum >= 0 && ackNum < LimitSeqNo && temp.length == Packet.SACKSIZE)) {
            System.out.println("Ack packet is corrupted!");
            corruptPktNum += 1;
            return;
        }

        ack_buffer_a.clear();
        // Allocate acks in sack to local ack buffer.
        for (int i = 0; i < temp.length; i++) {
            if (temp[i] >= 0 && temp[i] < LimitSeqNo) {
                ack_buffer_a.add(temp[i]);
            }
        }

        System.out.println("Packet received at A with ack number " + originAckNum);
        // Stop timer when ack received.
        if (timerFlag_a) {
            stopTimer(0);
            timerFlag_a = false;
        }
        if (ackNum == lastAckNum_a) {
            System.out.println("Duplicate ack received, resend next missing packets.");
            resendPacket();
        } else {
            if (ackNum < lastAckNum_a) {
                ackNum += LimitSeqNo;
            }
            // use reception time of the ack to calculate RTT (if the sent time is recorded in map)
            if (sendTime.containsKey(originAckNum - 1)) {
                RTTList.add(getTime() - sendTime.get(originAckNum - 1));
            }

            // Remove packets from resentBuffer and send same amount of new packets.
            for (int i = 0; i < (ackNum - lastAckNum_a); i++) {
                resentBuffer_a.remove(0);
                // use reception time of the ack to calculate CT for all packets corresponded by this ack.
                CTList.add(getTime() - oriSendTime.get(((originAckNum - 1 - i) % LimitSeqNo + LimitSeqNo) % LimitSeqNo));
                sendPacket();
            }
        }
        lastAckNum_a = originAckNum;
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("Timeout, resend missing packets.");
        if (timerFlag_a) {
            stopTimer(0);
            timerFlag_a = false;
        }
        resendPacket();
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        unsentBuffer_a = new LinkedList<>();
        resentBuffer_a = new ArrayList<>();
        nextSeqNum_a = 0;
        lastAckNum_a = 0;

        ack_buffer_a = new ArrayList<>();
    }

    /**
     * Convenient method to send back ACK to A
     */
    protected void b_send_ACK(int ack, int[] sack) {
        Packet p = new Packet(-1, ack, -1, sack);
//        printsack(ack,sack);
        toLayer3(B, p);
        ackPktNum += 1;
    }

    /**
     * Easy way to debug sack
     */
    protected void printsack(int ack, int[] sack) {
        System.out.println("ACK sent by B: " + ack);
        System.out.print("SACK sent by B: ");
        for (int i = 0; i < sack.length; i++) {
            System.out.print(sack[i]);
        }
        System.out.println();
    }

    protected void printArray(ArrayList<Integer> array) {
        System.out.print("Sorted sack in B: ");
        for (int i = 0; i < array.size(); i++) {
            System.out.print(array.get(i));
        }
        System.out.println();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.

    protected void bInput(Packet packet) {
        String msg = packet.getPayload();
        int p_seq = packet.getSeqnum();
        int checksum = msg.hashCode();
        /**Check the corruption of packets*/
        if (packet.getChecksum() != checksum || p_seq < 0 || p_seq >= LimitSeqNo || packet.getAcknum() != -1 || packet.getsack() != null) {
            System.out.println("Checksum failed, packet from A is corrupted!");
            corruptPktNum += 1;
            return;
        }
        System.out.println("Packet received at B with seq number " + p_seq + ", payload: " + msg);

        /**If the input packet's sequence number equals the wanted, it will be uploaded and the buffer_SACK will be checked
         * for the subsequent packets to be uploaded together.*/
        if (p_seq == wanted_B) {
            int end_window = (wanted_B + WindowSize - 1) % LimitSeqNo;
            toLayer5(packet.getPayload());
            deliveredPktNum++;
            wanted_B = (wanted_B + 1) % LimitSeqNo;
            /**Helper data structure to sort the buffer_SACK(since it is only decided by arrival)*/
            ArrayList<Integer> seq_number_sort = new ArrayList<>(buffer_SACK);
            if (buffer_SACK.isEmpty()) {
                for (int i = 0; i < 5; i++) {
                    SACK_B[i] = -1;
                }
                b_send_ACK(wanted_B, SACK_B);
                return;
            } else {
                /**The case that the end_window is on the right hand of wanted_B. Just sort and plugin*/
                if (end_window > wanted_B) {
                    seq_number_sort.sort(Comparator.naturalOrder());
                    printArray(seq_number_sort);
                    while (seq_number_sort.size() != 0) {
                        int seq = seq_number_sort.get(0);
                        if (seq > wanted_B) {
                            break;
                        } else if (seq < wanted_B) {  /**Delete the duplicate packets due to loss or timeout etc.*/
                            int result = seq_number_sort.remove(0);
                            buffer_SACK.remove((Object) result);
                        } else {
                            toLayer5(seq_to_packet.get(seq).getPayload());
                            deliveredPktNum++;
                            wanted_B = (wanted_B + 1) % LimitSeqNo;
                            int result = seq_number_sort.remove(0);
                            buffer_SACK.remove((Object) result);
                        }

                    }
                } else {
                    /**The case that the end_window is on the left hand of wanted_B. Require the sequence added with
                     * LimitedSeqNo to show its behind order.*/
                    int temp_wanted = wanted_B;
                    for (int i = 0; i < seq_number_sort.size(); i++) {
                        if (seq_number_sort.get(i) < end_window) {
                            seq_number_sort.set(i, seq_number_sort.get(i) + LimitSeqNo);
                        }
                    }
                    seq_number_sort.sort(Comparator.naturalOrder());
//                    printArray(seq_number_sort);
                    while (seq_number_sort.size() != 0) {
                        int seq = seq_number_sort.get(0);
                        if (seq > temp_wanted) {
                            break;
                        } else if (seq < temp_wanted) {  /**Delete the duplicate packets due to loss or timeout etc.*/
                            int result = seq_number_sort.remove(0);
                            buffer_SACK.remove((Object) (result % LimitSeqNo));
                        } else {
                            toLayer5(seq_to_packet.get(seq % LimitSeqNo).getPayload());
                            deliveredPktNum++;
                            temp_wanted = temp_wanted + 1;
                            int result = seq_number_sort.remove(0);
                            buffer_SACK.remove((Object) (result % LimitSeqNo));
                        }

                    }
                    wanted_B = temp_wanted % LimitSeqNo;

                }
            }

            /**Reset the cleared sack buffer to send back*/
            if (buffer_SACK.isEmpty()) {
                for (int i = 0; i < 5; i++) {
                    SACK_B[i] = -1;
                }
            } else {
                for (int i = 0; i < 5; i++) {
                    if (i < buffer_SACK.size()) {
                        SACK_B[i] = buffer_SACK.get(i);
                    } else {
                        SACK_B[i] = -1;
                    }

                }
            }
            b_send_ACK(wanted_B, SACK_B);
        } else {
            /**If the p_seq is inside the window of B, add to sack*/
            int end_window = (wanted_B + WindowSize) % LimitSeqNo;

            if (end_window > wanted_B) { /**The right end, adding packet to buffers.*/
                if (p_seq <= end_window && p_seq > wanted_B) {
                    if (buffer_SACK.size() < 5) {
                        buffer_SACK.add(packet.getSeqnum());
                        seq_to_packet.put(p_seq, packet);
                    } else {
                        buffer_SACK.remove(0);
                        buffer_SACK.add(buffer_SACK.size(), p_seq);
                        seq_to_packet.put(p_seq, packet);
                    }
                }
            } else {
                if (p_seq <= end_window || p_seq > wanted_B) {/**The left end, adding packet to buffers.*/
                    if (buffer_SACK.size() < 5) {
                        buffer_SACK.add(packet.getSeqnum());
                        seq_to_packet.put(p_seq, packet);
                    } else {
                        buffer_SACK.remove(0);
                        buffer_SACK.add(buffer_SACK.size(), p_seq);
                        seq_to_packet.put(p_seq, packet);

                    }

                }
            }
            /**Reset SACK for adding the buffer_SACK into it*/
            for (int i = 0; i < 5; i++) {
                if (i < buffer_SACK.size()) {
                    SACK_B[i] = buffer_SACK.get(i);
                } else {
                    SACK_B[i] = -1;
                }
            }

            b_send_ACK(wanted_B, SACK_B);
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        buffer_SACK = new ArrayList<>();
        seq_to_packet = new HashMap<>();
        wanted_B = 0;
        SACK_B = new int[5];
        for (int i = 0; i < 5; i++) {
            SACK_B[i] = -1;
        }
    }

    // Use to print final statistics
    protected void Simulation_done() {
        // Calculate total RTT & communication time.
        Double totalRTT = Double.valueOf(0);
        for (int i = 0; i < RTTList.size(); i++) {
            totalRTT += RTTList.get(i);
        }

        Double totalCT = Double.valueOf(0);
        for (int i = 0; i < CTList.size(); i++) {
            totalCT += CTList.get(i);
        }
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + originPktNum);
        System.out.println("Number of retransmissions by A:" + retransPktNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + deliveredPktNum);
        System.out.println("Number of ACK packets sent by B:" + ackPktNum);
        System.out.println("Number of corrupted packets:" + corruptPktNum);
        System.out.println("Ratio of lost packets:" + ((double) retransPktNum - (double) corruptPktNum) / ((double) originPktNum + (double) retransPktNum + (double) ackPktNum));
        System.out.println("Ratio of corrupted packets:" + (double) corruptPktNum / ((double) originPktNum + (double) retransPktNum + (double) ackPktNum - (double) retransPktNum + (double) corruptPktNum));
        System.out.println("Average RTT:" + totalRTT / RTTList.size());
        System.out.println("Average communication time:" + totalCT / CTList.size());
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        System.out.println("Throughput: " + ((double) originPktNum + (double) retransPktNum) * 20 / getTime());
        System.out.println("Goodput: " + (double) originPktNum * 20 / getTime());
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}
