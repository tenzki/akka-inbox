package persistence;

import java.io.Serializable;

public class Messages {

    public static class Msg implements Serializable {
        public final long deliveryId;
        public final String s;

        public Msg(long deliveryId, String s) {
            this.deliveryId = deliveryId;
            this.s = s;
        }
    }

    public static class Confirm implements Serializable {
        public final long deliveryId;

        public Confirm(long deliveryId) {
            this.deliveryId = deliveryId;
        }
    }

    public static class MsgSent implements Serializable {
        public final String s;

        public MsgSent(String s) {
            this.s = s;
        }
    }

    public static class MsgConfirmed implements Serializable {
        public final long deliveryId;

        public MsgConfirmed(long deliveryId) {
            this.deliveryId = deliveryId;
        }
    }

    public static class Snap implements Serializable {}

}
