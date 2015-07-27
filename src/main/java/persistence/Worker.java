package persistence;

import akka.actor.Props;
import akka.actor.UntypedActor;

public class Worker extends UntypedActor {

    public static Props props() {
        return Props.create(Worker.class);
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof Messages.Msg) {
            Messages.Msg msg = (Messages.Msg) message;
            Thread.sleep(5000);
            System.out.println("message delivered " + msg.s + " " + msg.deliveryId);
            getSender().tell(new Messages.Confirm(msg.deliveryId), getSelf());
        } else {
            unhandled(message);
        }
    }

}