package persistence;

import akka.actor.*;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActor;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.typesafe.config.*;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class PersistenceTest {

    static ActorSystem system;

    static MongoCollection<Document> journal;
    static MongoCollection<Document> snaps;

    public PersistenceTest() {
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);

        final MongoClient mongoClient = new MongoClient("localhost", 27017);
        final MongoDatabase database = mongoClient.getDatabase("akka-inbox-test");
        journal = database.getCollection("akka_persistence_journal");
        snaps = database.getCollection("akka_persistence_snaps");
    }

    @BeforeClass
    public static void setup() {
        Config config = ConfigFactory.load(ConfigParseOptions.defaults())
                .withValue("akka.contrib.persistence.mongodb.mongo.mongouri", ConfigValueFactory.fromAnyRef("mongodb://localhost:27017/akka-inbox-test"))
                .withValue("akka.persistence.at-least-once-delivery.redeliver-interval", ConfigValueFactory.fromAnyRef("1s"));
        system = ActorSystem.create("persistence-test", config);
    }

    @After
    public void after() {
        journal.deleteMany(new BasicDBObject());
        snaps.deleteMany(new BasicDBObject());
    }

    @AfterClass
    public static void tearDown() {
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


            assertEquals(2, journal.count());
            assertEquals(0, snaps.count());
        }};
    }

    @Test
    public void testAssignerSnapshot() {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));

            ref.tell("a", getRef());
            probe.expectMsgClass(Messages.Msg.class);

            ref.tell(new Messages.Snap(), getRef());
            probe.expectMsgClass(Messages.Msg.class);

            assertEquals(0, journal.count());
            assertEquals(1, snaps.count());
        }};
    }

    @Test
    public void testAssignerSnapshotRedeliver() {
        new JavaTestKit(system) {{
            final JavaTestKit probe = new JavaTestKit(system);
            final ActorRef ref = system.actorOf(Assigner.props(probe.getRef().path()));

            ref.tell("a", getRef());
            probe.expectMsgClass(Messages.Msg.class);
            ref.tell(new Messages.Snap(), getRef());

            probe.expectNoMsg(duration("1 s"));

            probe.watch(ref);
            ref.tell(PoisonPill.getInstance(), getRef());
            probe.expectMsgClass(Terminated.class);

            system.actorOf(Assigner.props(probe.getRef().path()));
            Messages.Msg msg = probe.expectMsgClass(Messages.Msg.class);
            probe.reply(new Messages.Confirm((msg).deliveryId));

            assertEquals(1, journal.count());
            assertEquals(1, snaps.count());
        }};
    }

}
