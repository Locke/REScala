package kofre.datatypes.experiments

import kofre.base.{Bottom, Lattice}
import kofre.datatypes.GrowOnlySet
import kofre.datatypes.experiments.AuctionInterface.Bid.User
import kofre.syntax.OpsSyntaxHelper

object AuctionInterface {
  sealed trait Status
  case object Open   extends Status
  case object Closed extends Status

  object Status {

    given ordering: Ordering[Status] with {
      override def compare(x: Status, y: Status): Int = if x == y then 0 else if x == Closed then 1 else -1
    }

    given lattice: Lattice[Status] = Lattice.fromOrdering
  }

  case class Bid(userId: User, bid: Int)

  case object Bid {
    type User = String
  }

  case class AuctionData(
      bids: Set[Bid] = Set.empty,
      status: Status = Open,
      winner: Option[User] = None
  )

  case object AuctionData {

    implicit val AuctionDataAsUIJDLattice: Lattice[AuctionData] = new Lattice[AuctionData] {
      override def lteq(left: AuctionData, right: AuctionData): Boolean = (left, right) match {
        case (AuctionData(lb, ls, _), AuctionData(rb, rs, _)) =>
          Lattice[Set[Bid]].lteq(lb, rb) && Lattice[Status].lteq(ls, rs)
      }

      override def decompose(state: AuctionData): Iterable[AuctionData] =
        state match {
          case AuctionData(bids, status, _) =>
            bids.map(b =>
              AuctionData(bids = Set(b))
            ) ++ (status match {
              case Open   => Set()
              case Closed => Set(AuctionData(status = Closed))
            })
        }

      override def merge(left: AuctionData, right: AuctionData): AuctionData = (left, right) match {
        case (AuctionData(lb, ls, _), AuctionData(rb, rs, _)) =>
          val bidsMerged   = Lattice[Set[Bid]].merge(lb, rb)
          val statusMerged = Lattice[Status].merge(ls, rs)
          val winnerMerged = statusMerged match {
            case Open   => None
            case Closed => bidsMerged.maxByOption(_.bid).map(_.userId)
          }

          AuctionData(bidsMerged, statusMerged, winnerMerged)
      }
    }
  }

  implicit class AuctionSyntax[C](container: C) extends OpsSyntaxHelper[C, AuctionData](container) {
    def bid(userId: User, price: Int): Mutate =
      AuctionData(bids = Set(Bid(userId, price))).mutator

    def close()(using PermMutate): C = AuctionData(status = Closed).mutator
  }
}
