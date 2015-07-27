package persistence;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersistenceTest {

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void tearDown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testWorkerActor() {
        new JavaTestKit(system) {{
            final ActorRef pingActor = system.actorOf(Worker.props());
            pingActor.tell(new Messages.Msg(1, "test"), getRef());

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
        }};
    }

}
