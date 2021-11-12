package ai2021.group53;

import geniusweb.actions.*;
import geniusweb.inform.*;
import geniusweb.issuevalue.Bid;
import geniusweb.opponentmodel.FrequencyOpponentModel;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import javax.websocket.DeploymentException;

import geniusweb.progress.ProgressTime;
import geniusweb.references.Parameters;
import tudelft.utilities.immutablelist.Tuple;
import tudelft.utilities.logging.Reporter;

public class Group53Party extends DefaultParty {

    private static final String MOPAC_PROTOCOL = "MOPAC";

    // Threshold for bids clustering
    private static final double TARGET_UTILITY = 0.9;
    private static final double BIDS_CLUSTERS_WIDTH = 0.05;

    // private Bid lastReceivedBid = null;
    private List<Offer> lastReceivedOffers = null;
    private List<Votes> lastReceivedVotes = null;
    private List<Bid> lastRejectedBids = new LinkedList<>();
    private Map<PartyId, Integer> powers = null;
    private Integer minPower = Integer.MIN_VALUE;
    private Integer maxPower = Integer.MAX_VALUE;

    protected ProfileInterface profileInterface;
    private PartyId me;
    private Progress progress;
    private String protocol;
    private Parameters sessionParameters;

    private BiddingStrategy biddingStrategy;
    private AcceptingStrategy acceptingStrategy;

    public Group53Party() {
    }

    public Group53Party(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void notifyChange(Inform info) {

        reporter.log(Level.SEVERE, "Hello there! Recieved a message.");

        try {

            //if (info instanceof SessionSettings)
            if (info instanceof Settings) {

                handleSettingsInform((Settings) info);

//            } else if (info instanceof ActionDone) {
//
//                handleActionDoneInform((ActionDone) info);
//
            } else if (info instanceof Voting) {

                lastReceivedOffers = ((Voting) info).getOffers();
                if (powers == null) {

                    powers = ((Voting) info).getPowers();

                }
                evaluateOffers();

                System.out.print("");

            } else if (info instanceof YourTurn) {

                makeOffer();

            } else if (info instanceof OptIn) {

                lastReceivedVotes = ((OptIn) info).getVotes();
                evaluateVotes();

            } else if (info instanceof Finished) {

                getReporter().log(Level.INFO, "Final outcome:" + info);

            }
        } catch (Exception e) {

            reporter.log(Level.SEVERE, "Exception on notifyChange. " + e.getMessage());

            throw new RuntimeException("Failed to handle info", e);
        }
        updateRound(info);
    }

    private void handleSettingsInform(Settings settingsInform) throws IOException, DeploymentException {

        this.sessionParameters = settingsInform.getParameters();

        this.profileInterface = ProfileConnectionFactory
            .create(settingsInform.getProfile().getURI(), getReporter());

        this.me = settingsInform.getID();
        this.progress = settingsInform.getProgress();
        this.protocol = settingsInform.getProtocol().getURI().getPath();

        UtilitySpace selfUtilitySpace = ((UtilitySpace) profileInterface.getProfile());

        HashMap<PartyId, FrequencyOpponentModel> opponentsModels = new HashMap<>();

        Bid reservationBid = profileInterface.getProfile().getReservationBid();

        double minAcceptableUtility;
        if (reservationBid == null) {
            minAcceptableUtility = 0;
        } else {
            minAcceptableUtility = selfUtilitySpace.getUtility(reservationBid).doubleValue();
        }

        this.biddingStrategy = new BiddingStrategy(
            profileInterface.getProfile().getDomain(),
            selfUtilitySpace,
            opponentsModels,
            BIDS_CLUSTERS_WIDTH,
            minAcceptableUtility
        );

        this.acceptingStrategy = new AcceptingStrategy(
            biddingStrategy,
            TARGET_UTILITY,
            selfUtilitySpace,
            minAcceptableUtility
        );

    }

//    private void handleActionDoneInform(ActionDone actionDoneInform) {
//        Action otherAction = actionDoneInform.getAction();
//
//        if (otherAction instanceof Offer) {
//            Offer offerAction = (Offer) otherAction;
//            lastReceivedBid = offerAction.getBid();
//            biddingStrategy.updateFrequencyModel(offerAction, progress);
//        }
//    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(
            new HashSet<>(Collections.singletonList(MOPAC_PROTOCOL)),
            Collections.singleton(Profile.class));
    }

    @Override
    public String getDescription() {
        return "places random bids until it can accept an offer with utility >0.7. "
            + "Parameters minPower and maxPower can be used to control voting behaviour.";
    }

    /**
     * Update {@link #progress}
     *
     * @param info the received info. Used to determine if this is the last info
     *             of the round
     */
    private void updateRound(Inform info) {

        if (protocol == null ||
            (protocol.equals(MOPAC_PROTOCOL) && !(info instanceof YourTurn))
        ) {
            return;
        }

        // if we get here, round must be increased.
        if (progress instanceof ProgressRounds) {
            progress = ((ProgressRounds) progress).advance();
        }

    }

    /**
     * send our next offer
     */
    private void makeOffer() throws IOException {
        Action action;
        List<Offer> offers = new LinkedList<>();

        if (lastReceivedOffers != null) {
            for (Offer lastReceivedOffer : lastReceivedOffers) {

                Bid lastReceivedBid = lastReceivedOffer.getBid();

    //            if ((protocol.equals(MOPAC_PROTOCOL)) && acceptingStrategy.shouldAcceptBid(lastReceivedBid)) {
    //
    //                getReporter().log(Level.INFO, "Accepting opponent offer");
    //
    //                action = new Accept(me, lastReceivedBid);
    //
    //            } else {

                getReporter().log(Level.INFO, "Making counter-offer");
                offers.add(new Offer(me, biddingStrategy.provideNextBidAndRemove(lastReceivedBid)));
                //action = new Offer(me, biddingStrategy.provideNextBidAndRemove(lastReceivedBid));

    //            }
            }

        } else {

            offers.add(new Offer(me, biddingStrategy.provideNextBidAndRemove(null)));

        }

        action = offers.get(0);

        getConnection().send(action);

    }

    private void evaluateOffers() throws IOException {

        HashSet<Vote> votes = new HashSet<Vote>();
        updateMinPower();
        updateMaxPower();

        for (Offer lastReceivedOffer : lastReceivedOffers) {

            Bid currentBid = lastReceivedOffer.getBid();
            boolean toAccept = acceptingStrategy.shouldAcceptBid(currentBid);

            if (toAccept) {
                Vote currentVote = new Vote(me, currentBid, minPower, maxPower);
                votes.add(currentVote);
            } else {
                lastRejectedBids.add(lastReceivedOffer.getBid());
            }

        }

        Action action = new Votes(me, votes);
        getConnection().send(action);

    }

    private void evaluateVotes() throws IOException {

        HashSet<Vote> votes = new HashSet<Vote>();
        HashMap<Bid, Tuple<Integer, Integer>> returnedOffers= new HashMap<>();

        for (Votes lastReceivedVotesFromOther : lastReceivedVotes) {

            for (Vote currentVote:
                 lastReceivedVotesFromOther.getVotes()) {

                if (lastRejectedBids.contains(currentVote.getBid())) {
                    Bid currentBid = currentVote.getBid();

                    if(!acceptingStrategy.shouldAcceptBid(currentBid))
                        continue;

                    if (!returnedOffers.containsKey(currentBid))
                        returnedOffers.put(currentBid, new Tuple<>(currentVote.getMinPower(), currentVote.getMaxPower()));
                    else {
                        Tuple<Integer, Integer> range = new Tuple<>(
                                Math.max(returnedOffers.get(currentBid).get1(), currentVote.getMinPower()),
                                Math.min(returnedOffers.get(currentBid).get2(), currentVote.getMaxPower())
                        );
                        returnedOffers.put(currentBid, range);
                    }
                }
            }
        }

        for (Bid bid : returnedOffers.keySet()) {
            Vote currentVote = new Vote(me, bid, returnedOffers.get(bid).get1(), returnedOffers.get(bid).get2());
            votes.add(currentVote);
        }

        Action action = new Votes(me, votes);
        getConnection().send(action);

    }

    private void updateMinPower() {
        if (powers != null)
            minPower = (powers.get(me));
    }

    private void updateMaxPower() {
        if (powers != null)
            maxPower =  (int) (powers.get(me) +
                    (powers.values().stream().reduce(0, Integer::sum) - powers.get(me)) * (1 - getProgressFactor(progress)));
    }

    /**
     * Get the progression of the negotiation as a double in range [0.0, 1.0]
     *
     * @param progress the progress object.
     * @return pregression of the negotiation.
     */
    private double getProgressFactor(Progress progress) {
        double progressLeft = 0.0;
        if (progress instanceof ProgressRounds) {
            ProgressRounds progressRound = (ProgressRounds) progress;
            progressLeft = (((double) progressRound.getTotalRounds() - (double) progressRound.getCurrentRound())
                    / (double) progressRound.getTotalRounds());
        } else if (progress instanceof ProgressTime) {
            ProgressTime progressTime = (ProgressTime) progress;
            progressLeft = 1.0D - ((double) System.currentTimeMillis() - (double) progressTime.getStart().getTime())
                    / (double) progressTime.getDuration();
        }
        return progressLeft;
    }

}
