package ai2021.group53;

import geniusweb.issuevalue.Bid;
import geniusweb.profile.utilityspace.UtilitySpace;

public class AcceptingStrategy {

    private final BiddingStrategy biddingStrategy;

    // Minimum utility level at which bids are accepted right away
    private final double targetUtility;

    private final UtilitySpace selfUtilitySpace;

    private final double minAcceptableUtility;

    public AcceptingStrategy(
        BiddingStrategy biddingStrategy,
        double targetUtility,
        UtilitySpace selfUtilitySpace,
        double minAcceptableUtility
    ) {

        this.biddingStrategy = biddingStrategy;
        this.targetUtility = targetUtility;
        this.selfUtilitySpace = selfUtilitySpace;
        this.minAcceptableUtility = minAcceptableUtility;

    }

    public final boolean shouldAcceptBid(Bid bid) {

        if(bid == null) {
            return false;
        }

        return isBetterThanTargetUtility(bid) || isBetterThanUpcomingBid(bid);

    }

    private boolean isBetterThanTargetUtility(Bid bid) {

        return selfUtilitySpace.getUtility(bid).doubleValue() >= targetUtility;

    }

    public boolean isBetterThanUpcomingBid(Bid opponentBid) {

        if (biddingStrategy.hasReachedReservationBid()) {

            return selfUtilitySpace.getUtility(opponentBid).doubleValue() >= minAcceptableUtility;

        } else {

            return selfUtilitySpace.getUtility(biddingStrategy.provideNextBid(opponentBid)).doubleValue() <=
                selfUtilitySpace.getUtility(opponentBid).doubleValue();

        }

    }

}
