public class Packet
{
    public static int SACKSIZE = 5;
    private int seqnum;
    private int acknum;
    private int checksum;
    private String payload;
    private int[] sack;

    public Packet(Packet p)
    {
        seqnum = p.getSeqnum();
        acknum = p.getAcknum();
        checksum = p.getChecksum();
        payload = new String(p.getPayload());
        sack=p.getsack();
    }

    public Packet(int seq, int ack, int check, String newPayload)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        sack = null;
        if (newPayload == null)
        {
            payload = "";
        }
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = null;
        }
        else
        {
            payload = new String(newPayload);
        }
    }

    public Packet(int seq, int ack, int check, String newPayload, int[] sck)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        sack = sck;
        if (newPayload == null)
        {
            payload = "";
        }
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = null;
        }
        else
        {
            payload = new String(newPayload);
        }
    }

    public Packet(int seq, int ack, int check, int[] sck)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        sack = sck;
        payload = "";
    }


    public boolean setSeqnum(int n)
    {
        seqnum = n;
        return true;
    }

    public boolean setAcknum(int n)
    {
        acknum = n;
        return true;
    }

    public boolean setChecksum(int n)
    {
        checksum = n;
        return true;
    }

    public boolean setPayload(String newPayload)
    {
        if (newPayload == null)
        {
            payload = "";
            return false;
        }
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = "";
            return false;
        }
        else
        {
            payload = new String(newPayload);
            return true;
        }
    }

    public int getSeqnum()
    {
        return seqnum;
    }

    public int getAcknum()
    {
        return acknum;
    }

    public int[] getsack()
    {
        return sack;
    }

    public int getChecksum()
    {
        return checksum;
    }

    public String getPayload()
    {
        return payload;
    }

    public String toString()
    {
        return("seqnum: " + seqnum + "  acknum: " + acknum + "  checksum: " +
                checksum + "  payload: " + payload);
    }

}
