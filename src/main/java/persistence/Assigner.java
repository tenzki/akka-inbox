package persistence;

import akka.actor.ActorPath;
import akka.actor.Props;
import akka.persistence.*;

class Assigner extends UntypedPersistentActorWithAtLeastOnceDelivery {

    private final ActorPath destination;

    public static Props props(ActorPath destination) {
        return Props.create(Assigner.class, destination);
    }

    @Override
    public String persistenceId() { return "job-id"; }

    public Assigner(ActorPath destination) {
        this.destination = destination;
    }

    @Override
    public void onReceiveCommand(Object message) {
        if (message instanceof String) {
            String s = (String) message;
            persist(new Messages.MsgSent(s), this::updateState);
        } else if (message instanceof Messages.Confirm) {
            Messages.Confirm confirm = (Messages.Confirm) message;
            persist(new Messages.MsgConfirmed(confirm.deliveryId), this::updateState);
        } else if(message instanceof Messages.Snap) {
            saveSnapshot(getDeliverySnapshot());
        } else if(message instanceof SaveSnapshotSuccess) {
            SnapshotMetadata metadata = ((SaveSnapshotSuccess) message).metadata();
            deleteSnapshots(new SnapshotSelectionCriteria(metadata.sequenceNr() - 1, metadata.timestamp() - 1));
            deleteMessages(metadata.sequenceNr());
        } else {
            unhandled(message);
        }
    }

    @Override
    public void onReceiveRecover(Object event) {
        updateState(event);
    }

    void updateState(Object event) {
        if (event instanceof Messages.MsgSent) {
            final Messages.MsgSent evt = (Messages.MsgSent) event;
            deliver(destination, (Long deliveryId) -> {
                return new Messages.Msg(deliveryId, evt.s);
            });
        } else if (event instanceof Messages.MsgConfirmed) {
            final Messages.MsgConfirmed evt = (Messages.MsgConfirmed) event;
            confirmDelivery(evt.deliveryId);
        } else if (event instanceof SnapshotOffer) {
            final SnapshotOffer snap = (SnapshotOffer) event;
            setDeliverySnapshot((AtLeastOnceDeliverySnapshot) snap.snapshot());
        }
    }
}
