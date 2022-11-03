import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class StudentNetworkSimulator extends NetworkSimulator
{
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
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Statistic Variables
    private int originPktNum;
    private int retransPktNum;
    private int deliveredPktNum;
    private int ackPktNum;
    private int corruptPktNum;
    private int receivedPktNum;






    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // Variables for the sender (A)
    // Stores the data that is not sent yet.
    private Queue<Packet> unsentBuffer_a;
    // Stores the data that is sent but might require resent.
    private Queue<Packet> resentBuffer_a;
    private int nextSeqNum_a;
    private int lastAckNum_a;
    private boolean timerFlag_a;
    private int currSeqNum_a;

    // Variables for the receiver (B)
    private Queue<Packet> buffer_B;
    //window notation
    private int wanted_B;
    private int head_B;
    private int[] SACK_B;



    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;

        originPktNum = 0;
        retransPktNum = 0;
        deliveredPktNum = 0;
        ackPktNum = 0;
        corruptPktNum = 0;
        receivedPktNum = 0;
        currSeqNum_a = 0;
    }

    // This function copies the head packet from the queue,
    // sent it and update relevant values.
    protected void sendPacket(Queue<Packet> q){
        if (q.isEmpty()){
            return;
        }
        System.out.println("Packet sent by A with seq number " + q.peek().getSeqnum());
        toLayer3(0, q.peek());
        if (timerFlag_a == false){
            startTimer(0, RxmtInterval);
            timerFlag_a = true;
        }
        if (q.equals(unsentBuffer_a)){
            resentBuffer_a.add(unsentBuffer_a.poll());
            originPktNum += 1;
        }
        else{
            retransPktNum += 1;
        }
    }


    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        System.out.println("Message received at A: " + message.getData());
        // Generate packet
        Packet pkt = new Packet(nextSeqNum_a, -1, message.getData().hashCode(), message.getData());
        unsentBuffer_a.add(pkt);
        System.out.println(resentBuffer_a);
        System.out.println(unsentBuffer_a);

        // Check if the packet can be sent right away.
        if (resentBuffer_a.size() < WindowSize){
            sendPacket(unsentBuffer_a);
        }
        // Increase the next sequence number.
        nextSeqNum_a += 1;
        if (nextSeqNum_a == 2*WindowSize){
            nextSeqNum_a = 0;
        }
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        /**需要改成array模式*/
        int originAckNum = packet.getAcknum();
        int ackNum = originAckNum;



        receivedPktNum += 1;
        System.out.println("Packet received at A with ack number " + ackNum);
        if (packet.getSeqnum() == -1 && packet.getPayload().equals("") && ackNum >= 0 && ackNum < 2*WindowSize){
            // Stop timer when ack received.
            if (timerFlag_a == true){
                stopTimer(0);
                timerFlag_a = false;
            }
            if (ackNum == lastAckNum_a){
                sendPacket(resentBuffer_a);
            }
            else {
                if (ackNum < lastAckNum_a) {
                    ackNum += 2*WindowSize;
                    System.out.println("Here: " + ackNum + " "+  lastAckNum_a);
                }
                for (int i = 0; i < (ackNum-lastAckNum_a); i++){
                    resentBuffer_a.poll();
                    sendPacket(unsentBuffer_a);
                }
            }
            lastAckNum_a = originAckNum;
        }
        else{
            System.out.println("Ack packet is corrupted!");
            corruptPktNum += 1;
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        System.out.println("Timeout, resend the next missing packet.");
        if (timerFlag_a == true){
            stopTimer(0);
            timerFlag_a = false;
        }
        sendPacket(resentBuffer_a);
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        unsentBuffer_a = new LinkedList<Packet>();
        resentBuffer_a = new LinkedList<Packet>();
        nextSeqNum_a = 0;
        lastAckNum_a = 0;
    }


    protected void b_send_ACK(int ack, int[] sack){
        Packet p = new Packet(-1,ack,-1,sack);
        System.out.println("Packet sent by B with ack number " + sack.toString());
        toLayer3(B,p);
        ackPktNum += 1;
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    //上传的+1
    protected void bInput(Packet packet)
    {
        receivedPktNum += 1;
        String msg = packet.getPayload();
        int p_seq = packet.getSeqnum();
        int checksum = msg.hashCode();
        if (packet.getChecksum() != checksum){
            System.out.println("corrupted");
            corruptPktNum++;
            return;
        }
        if (p_seq == wanted_B){
            toLayer5(packet.getPayload());
            if (buffer_B.size()<5){
                buffer_B.add(packet);
                int temp_tail = p_seq;
                SACK_B[temp_tail] = p_seq;
            } else{
                buffer_B.add(packet);
                buffer_B.poll();
                head_B = buffer_B.peek().getSeqnum();
                for (int i = 0; i < 5; i++) {
                    int temp = (head_B+i)%LimitSeqNo;
                    SACK_B[i] = temp;
                }
            }
            /**Make sure what the limit will be for A*/
            wanted_B = (wanted_B+1)%LimitSeqNo;
            b_send_ACK(wanted_B, SACK_B);
        }else {
            b_send_ACK(wanted_B, SACK_B);
        }


    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        buffer_B = new LinkedList<>();
        wanted_B = 0;
        head_B = 0;
        SACK_B = new int[5];
        for (int i = 0; i < 5; i++) {
            SACK_B[i] = -1;
        }
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + originPktNum);
        System.out.println("Number of retransmissions by A:" + retransPktNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + deliveredPktNum);
        System.out.println("Number of ACK packets sent by B:" + ackPktNum);
        System.out.println("Number of corrupted packets:" + corruptPktNum);
        System.out.println("Ratio of lost packets:" + (1.0-((double)receivedPktNum/((double)originPktNum+(double)retransPktNum+(double)ackPktNum))) );
        System.out.println("Ratio of corrupted packets:" + (double)corruptPktNum/((double)originPktNum+(double)retransPktNum+(double)ackPktNum));
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}
