package persistence;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class ApplicationMain {

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("persistence");

        ActorRef worker = system.actorOf(Worker.props(), "worker");
        ActorRef assigner = system.actorOf(Assigner.props(worker.path()), "assigner");

//        assigner.tell("a", null);
        system.scheduler().schedule(Duration.Zero(), Duration.create(2, TimeUnit.SECONDS), assigner, "a", system.dispatcher(), null);
        system.scheduler().schedule(Duration.Zero(), Duration.create(10, TimeUnit.SECONDS), assigner, new Messages.Snap(), system.dispatcher(), null);
    }
}
