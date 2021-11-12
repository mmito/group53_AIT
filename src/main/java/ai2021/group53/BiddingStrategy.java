package ai2021.group53;

import geniusweb.actions.Action;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.opponentmodel.FrequencyOpponentModel;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.progress.Progress;

import java.util.*;

public class BiddingStrategy {

    // Bids clustered by utility, sorted in descending order.
    private final List<Set<Bid>> bidClusters;


    private HashMap<PartyId, FrequencyOpponentModel> opponents;
    // private FrequencyOpponentModel frequencyOpponentModel;

    private final Set<Bid> acceptableBids;

    public BiddingStrategy(
        Domain domain,
        UtilitySpace selfUtilitySpace,
        HashMap<PartyId, FrequencyOpponentModel> opponents,
        double bidClusterWidth,
        double minAcceptableUtility
    ) {

        if (bidClusterWidth <= 0 || bidClusterWidth > 1) {

            throw new IllegalArgumentException("cluster width must be greater than 0 and smaller or equal to 1");

        }

        this.opponents = opponents;

        this.acceptableBids = getAllBidsWithMinUtility(domain, minAcceptableUtility, selfUtilitySpace);

        // Initialize bid clusters
        this.bidClusters = generateBidsClusters(
            this.acceptableBids,
            selfUtilitySpace,
            bidClusterWidth
        );

    }

    private Set<Bid> getAllBidsWithMinUtility(Domain domain, double minUtility, UtilitySpace utilitySpace) {

        Set<Bid> result = new HashSet<>();

        if (minUtility <= 0) {

            for (Bid bid : new AllBidsList(domain)) {

                result.add(bid);

            }

            return result;

        } else {

            for (Bid bid : new AllBidsList(domain)) {

                if (utilitySpace.getUtility(bid).doubleValue() >= minUtility) {

                    result.add(bid);

                }

            }

        }

        return result;

    }

    public void updateFrequencyModel(Action opponentAction, Progress progress) {

        if (this.opponents.containsKey(opponentAction.getActor())) {

            FrequencyOpponentModel opponentModel = opponents.get(opponentAction.getActor());
            opponents.put(opponentAction.getActor(), opponentModel.with(opponentAction, progress));

        } else {

            opponents.put(opponentAction.getActor(), new FrequencyOpponentModel().with(opponentAction, progress));

        }

    }

    private static int getBidClusterNumber(double utility, double clusterWidth) {

        return (int) Math.round(utility / clusterWidth);

    }

    private static int getHighestPossibleBidClusterNumber(double clusterWidth) {

        return (int) Math.ceil(1 / clusterWidth);

    }

    private List<Set<Bid>> generateBidsClusters(
        Set<Bid> bids,
        UtilitySpace selfUtilitySpace,
        double bidClusterWidth
    ) {

        LinkedHashMap<Integer, Set<Bid>> bidSetsMap = new LinkedHashMap<>();

        // Initializing all bid sets
        for (int i = getHighestPossibleBidClusterNumber(bidClusterWidth); i >= 0; i--) {

            bidSetsMap.put(i, new HashSet<>());

        }

        // Populating bid clusters
        for (Bid bid : bids) {

            double bidUtility = selfUtilitySpace.getUtility(bid).doubleValue();

            bidSetsMap.get(getBidClusterNumber(bidUtility, bidClusterWidth)).add(bid);

        }

        // Storing only non-empty clusters
        List<Set<Bid>> bidClusters = new LinkedList<>();

        for (Set<Bid> bidSet : bidSetsMap.values()) {

            if (bidSet.size() > 0) {

                bidClusters.add(bidSet);

            }


        }

        return bidClusters;

    }

    public boolean hasReachedReservationBid() {

        return bidClusters.size() == 0;

    }

    public Bid provideNextBid(Bid opponentBid) {

        if (bidClusters.size() == 0) {

            return computeAveragedReservationBid();

        } else {

            return getBidWithHighestAverageUtility(
                bidClusters.get(0),
                opponents
            );

        }

    }

    public Bid provideNextBidAndRemove(Bid opponentBid) {

        if (bidClusters.size() == 0) {

            return computeAveragedReservationBid();

        } else {

            Bid nextBid = getBidWithHighestAverageUtility(
                bidClusters.get(0),
                opponents
            );

            if (bidClusters.get(0).size() == 1) {

                // Remove empty bid cluster
                bidClusters.remove(0);

            } else {

                // Remove bid from cluster
                bidClusters.get(0).remove(nextBid);

            }

            return nextBid;

        }

    }

//    private Bid computeReservationBid() {
//
//        return getBidWithHighestUtility(acceptableBids, frequencyOpponentModel);
//
//    }

    private Bid computeAveragedReservationBid() {

        return getBidWithHighestAverageUtility(acceptableBids, opponents);

    }

//    private Bid getBidWithHighestUtility(Set<Bid> bids, UtilitySpace utilitySpace) {
//
//        return bids.stream().max((o1, o2) -> {
//
//            double u1 = utilitySpace.getUtility(o1).doubleValue();
//            double u2 = utilitySpace.getUtility(o2).doubleValue();
//
//            return Double.compare(u1, u2);
//
//        }).orElseThrow(() -> new IllegalArgumentException("bid set is empty"));
//
//    }

    private Bid getBidWithHighestAverageUtility(Set<Bid> bids, HashMap<PartyId, FrequencyOpponentModel> opponents) {

        return bids.stream().max((o1, o2) -> {

            double u1 = 0;
            double u2 = 0;

            for (PartyId key : opponents.keySet()) {

                u1 += opponents.get(key).getUtility(o1).doubleValue();
                u2 += opponents.get(key).getUtility(o2).doubleValue();

            }

            return Double.compare(u1, u2);

        }).orElseThrow(() -> new IllegalArgumentException("bid set is empty"));

    }

}
