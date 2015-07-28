package persistence;

import akka.actor.*;
import akka.persistence.SnapshotSelectionCriteria;
import akka.persistence.UntypedPersistentActor;
import akka.persistence.UntypedPersistentView;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActor;
import com.typesafe.config.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class PersistenceTest {

    static class UtilActor extends UntypedPersistentActor {

        public static Props props() {
            return Props.create(UtilActor.class);
        }

        @Override
        public String persistenceId() {
            return "job-id";
        }

        @Override
        public void onReceiveCommand(Object message) throws Exception {
            if(message.equals("cleanup")) {
                deleteMessages(Long.MAX_VALUE);
                deleteSnapshots(new SnapshotSelectionCriteria(Long.MAX_VALUE, Long.MAX_VALUE));
            }
        }

        @Override
        public void onReceiveRecover(Object message) throws Exception {
            // left empty
        }

    }

    static ActorSystem system;
    static ActorRef util;

    @BeforeClass
    public static void setup() {
        Config config = ConfigFactory.load(ConfigParseOptions.defaults())
                .withValue("akka.contrib.persistence.mongodb.mongo.mongouri", ConfigValueFactory.fromAnyRef("mongodb://localhost:27017/akka-inbox-test"))
                .withValue("akka.persistence.at-least-once-delivery.redeliver-interval", ConfigValueFactory.fromAnyRef("1s"));
        system = ActorSystem.create("persistence-test", config);
        util = system.actorOf(UtilActor.props());
    }

    @AfterClass
    public static void tearDown() {
        util.tell("cleanup", ActorRef.noSender());
        JavaTestKit.shutdownActorSystem(system, Duration.apply(10, TimeUnit.SECONDS), false);
        system = null;
    }

    @Test
    public void testWorkerActor() {
        new JavaTestKit(system) {{
            final ActorRef worker = system.actorOf(Worker.props());
            worker.tell(new Messages.Msg(1, "test"), getRef());

            final Long result = new ExpectMsg<Long>("response") {
                // do not put code outside this method, will run afterwards
                protected Long match(Object in) {
                    if (in instanceof Messages.Confirm) {
                        return ((Messages.Confirm) in).deliveryId;
                    }
                    throw noMatch();
                }
            }.get(); // this extracts the received message

            assertEquals(Long.valueOf(1), result);
            expectNoMsg(duration("1 s"));
        }};
    }

    @Test
    public void testAssignerActor() throws InterruptedException {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            probe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell(new Messages.Confirm(((Messages.Msg) msg).deliveryId), probe.getRef());
                    return noAutoPilot();
                }
            });

            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));
            ref.tell("a", getRef());

            probe.expectMsgClass(Messages.Msg.class);
            probe.expectNoMsg(duration("1 s"));
        }};
    }

    @Test
    public void testAssignerActorResending() throws InterruptedException {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));
            ref.tell("a", getRef());

            probe.expectMsgClass(Messages.Msg.class);
            Messages.Msg msg = probe.expectMsgClass(Messages.Msg.class);
            probe.reply(new Messages.Confirm((msg).deliveryId));
            probe.expectNoMsg(duration("1 s"));
        }};
    }

    @Test
    public void testAssignerRecreated() {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));
            probe.watch(ref);
            ref.tell("a", getRef());

            probe.expectMsgClass(Messages.Msg.class);
            ref.tell(PoisonPill.getInstance(), getRef());

            probe.expectMsgClass(Terminated.class);

            system.actorOf(Assigner.props(probe.getRef().path()));
            Messages.Msg msg = probe.expectMsgClass(Messages.Msg.class);
            probe.reply(new Messages.Confirm((msg).deliveryId));
            probe.expectNoMsg(duration("1 s"));
        }};
    }


    @Test
    public void testAssignerSnapshot() {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            util.tell(probe.getRef(), getRef());
            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));
            probe.watch(ref);
            ref.tell("a", getRef());

            probe.expectMsgClass(Messages.Msg.class);
            ref.tell(new Messages.Snap(), getRef());

            probe.expectNoMsg(duration("1 s"));

            ref.tell(PoisonPill.getInstance(), getRef());
            probe.expectMsgClass(Terminated.class);

            system.actorOf(Assigner.props(probe.getRef().path()));
            Messages.Msg msg = probe.expectMsgClass(Messages.Msg.class);
            probe.reply(new Messages.Confirm((msg).deliveryId));
            probe.expectNoMsg(duration("1 s"));
        }};
    }

}
