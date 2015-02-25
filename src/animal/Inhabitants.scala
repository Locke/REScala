package animal

import rescala._
import rescala.turns.Engines.default
import rescala.turns.Ticket


object Animal {
  val StartEnergy = 200
  // radius that animals can see the world around them
  val ViewRadius = 9
  // energy required to move
  val MoveCost = 1
  // energy required to procreate
  val ProcreateCost = 10
  // maximum age in days when an animal dies, regardless of energy
  val MaxAge = 25
  // energy rate gained when eating plants
  val PlantEatRate = 3
  // minimum energy required for male animals to seek a mate
  val ProcreateThreshold = 60
  // minimum age in days for animals to be fertile
  val FertileAge = 1
  // time in hours for female sheep to be pregnant
  val PregnancyTime = 30
  // minimum energy for carnivores to attack
  val AttackThreshold = 100
  // energy stolen when carnivores attack
  val AttackAmount = 50
  // minimum energy for carnivores to start sleeping
  val SleepThreshold = 30
  // energy gained while sleeping
  val SleepRate = 2
}

abstract class Animal(implicit world: World) extends BoardElement {


  override def isAnimal: Boolean = true

  /** An animal is in a state */
  sealed trait AnimalState
  case object Idling extends AnimalState
  case class Eating(plant: Plant) extends AnimalState
  case class Attacking(other: Animal) extends AnimalState
  case class Moving(dir: Pos) extends AnimalState
  case class Procreating(female: Animal) extends AnimalState
  case object FallPrey extends AnimalState
  case object Sleeping extends AnimalState

  val state: Var[AnimalState] = Var(Idling) //#VAR

  // partial function for collecting food, dependant on state of the object
  val findFood: Signal[PartialFunction[BoardElement, BoardElement]] // Abstract (//#SIG)

  // function for creating a state upon reaching target
  def reachedState(target: BoardElement): AnimalState


  def savage() = state.set(FallPrey)

  protected def nextAction(pos: Pos): AnimalState = {
    val neighbors = world.board.neighbors(pos)
    val food = neighbors.collectFirst(findFood.now)
    val nextAction: AnimalState = food match {
      case Some(target) => reachedState(target) // I'm near food, eat it!
      case None => // I have to look for food nearby
        world.board.nearby(pos, Animal.ViewRadius).collectFirst(findFood.now) match {
          case Some(target) =>
            val destination = world.board.getPosition(target)
            if (destination.isDefined)
              Moving(pos.directionTo(destination.get))
            else
              randomMove
          case None => randomMove
        }
    }
    nextAction
  }

  protected def randomMove: AnimalState = {
    val randx = 1 - world.randomness.nextInt(3)
    val randy = 1 - world.randomness.nextInt(3)
    Moving(Pos(randx, randy))
  }


  val age: Signal[Int] = world.time.day.changed.iterate(1)(_ + 1) //#SIG //#IF //#IF

  val isAdult = age.map(_ > Animal.FertileAge)
  val isFertile = isAdult
  val isEating = state map {
    case Eating(_) => true
    case _ => false
  }

  val energyDrain = Signals.lift(age, state, world.board.animalsAlive) { (a, s, alive) =>
    (alive / (world.board.width + world.board.height)) +
      (a / 2) +
      (s match {
        case Moving(_) => Animal.MoveCost
        case Procreating(_) => Animal.ProcreateCost
        case FallPrey => Animal.AttackAmount
        case _ => 0
      })
  }

  val energyGain =
    state map {
      case Eating(_) => Animal.PlantEatRate
      case Sleeping => Animal.SleepRate
      case Attacking(prey) => Animal.AttackAmount
      case _ => 0
    }

  // we do not have a built in method for this kind of “fold some snapshot” but its not that hard to write one
  val energy: Signal[Int] =
    implicitly[Ticket].apply(Signals.Impl.makeStatic(Set(world.time.tick, energyDrain, energyGain), Animal.StartEnergy) {
      (turn, current) => world.time.tick.pulse(turn).fold(current, _ => current + energyGain.get(turn) - energyDrain.get(turn))
    })

  override val isDead = Signals.lift(age, energy) { (a, e) => a > Animal.MaxAge || e < 0 }

  /** imperative 'AI' function */
  override def doStep(pos: Pos): Unit = {
    state.now match {
      case Moving(dir) => world.board.moveIfPossible(pos, dir)
      case Eating(plant) => plant.takeEnergy(energyGain.now)
      case Attacking(prey) => prey.savage()
      case Procreating(female: Female) => female.procreate(this)
      case _ =>
    }
    state.set(nextAction(pos))
  }
}

class Carnivore(implicit world: World) extends Animal {

  val sleepy = energy map { _ < Animal.SleepThreshold }
  val canHunt = energy map { _ > Animal.AttackThreshold }

  // only adult carnivores with min energy can hunt, others eat plants
  val findFood: Signal[PartialFunction[BoardElement, BoardElement]] = Signals.static(isAdult, canHunt) { t =>
    if (isAdult.get(t) && canHunt.get(t)) { case p: Herbivore => p }: PartialFunction[BoardElement, BoardElement]
    else { case p: Plant => p }: PartialFunction[BoardElement, BoardElement]
  }


  override def reachedState(prey: BoardElement): AnimalState = prey match {
    case p: Herbivore => Attacking(p)
    case _ => Idling
  }


  override protected def nextAction(pos: Pos): AnimalState = {
    if (sleepy.now) Sleeping
    else super.nextAction(pos)
  }
}

class Herbivore(implicit world: World) extends Animal {

  val findFood: Signal[PartialFunction[BoardElement, BoardElement]] = //#SIG
    Var {
      { case p: Plant => p }: PartialFunction[BoardElement, BoardElement]
    }

  override def reachedState(plant: BoardElement): AnimalState = plant match {
    case p: Plant => Eating(p)
    case _ => Idling
  }
}

trait Female extends Animal {

  val mate: Var[Option[Animal]] = Var(None) //#VAR

  val isPregnant = mate.map { _.isDefined } //#SIG

  val becomePregnant: Event[Unit] = isPregnant.changedTo(true) //#EVT //#IF

  // counts down to 0
  lazy val pregnancyTime: Signal[Int] = becomePregnant.reset(()) { _ => //#SIG  //#IF
    world.time.hour.changed.iterate(Animal.PregnancyTime)(_ - (if (isPregnant.now) 1 else 0)) //#IF //#IF //#SIG
  }


  lazy val giveBirth: Event[Unit] = pregnancyTime.changedTo(0) //#EVT //#IF

  override val isFertile = Signals.lift(isAdult, isPregnant) { _ && !_ } //#SIG

  // override val energyDrain = Signal { super.energyDrain() * 2 }
  // not possible

  giveBirth += { _ => //#HDL
    world.plan {
      val father = mate.now.get
      val child = createOffspring(father)
      world.board.getPosition(this).foreach { mypos =>
        world.board.nearestFree(mypos).foreach { target =>
          world.spawn(child, target)
        }
      }
      mate.set(None)
    }
  }

  def procreate(father: Animal): Unit = {
    if (isPregnant.now) return
    mate.set(Some(father))
  }


  def createOffspring(father: Animal): Animal = {
    val male = world.randomness.nextBoolean()
    val nHerbivores = List(this, father).map(_.isInstanceOf[Herbivore]).count(_ == true)
    val herbivore =
      if (nHerbivores == 0) false // both parents are a carnivores, child is carnivore
      else if (nHerbivores == 2) true // both parents are herbivores, child is herbivore
      else world.randomness.nextBoolean() // mixed parents, random

    world.newAnimal(herbivore, male)
  }
}


trait Male extends Animal {
  val seeksMate = Signals.lift(isFertile, energy) { _ && _ > Animal.ProcreateThreshold }

  override def nextAction(pos: Pos): AnimalState = {
    if (seeksMate.now) {
      val findFemale: PartialFunction[BoardElement, Female] = {
        case f: Female if f.isFertile.now => f
      }
      val neighbors = world.board.neighbors(pos)
      val females = neighbors.collectFirst(findFemale)

      val nextAction: AnimalState = females match {
        case Some(female) => Procreating(female)
        case None => // I have to look for females nearby
          world.board.nearby(pos, Animal.ViewRadius).collectFirst(findFemale) match {
            case Some(target) =>
              val destination = world.board.getPosition(target)
              if (destination.isDefined) Moving(pos.directionTo(destination.get))
              else super.nextAction(pos)
            case None => super.nextAction(pos)
          }
      }
      nextAction
    }
    else super.nextAction(pos)
  }
}


class FemaleHerbivore(implicit world: World) extends Herbivore with Female
class MaleHerbivore(implicit world: World) extends Herbivore with Male
class FemaleCarnivore(implicit world: World) extends Carnivore with Female
class MaleCarnivore(implicit world: World) extends Carnivore with Male


object Plant {
  val Energy = 100
  val GrowTime = 50
  // after how many hours plant grows (increments size)
  val MaxSize = 6 // max size a plant reaches. then expands
}

class Plant(implicit world: World) extends BoardElement {


  override def isAnimal: Boolean = false

  val energy = Var(Plant.Energy)

  val isDead = energy map (_ <= 0)

  val age: Signal[Int] = world.time.hour.changed.iterate(0)(_ + 1)
  val grows: Event[Int] = age.changed && { _ % Plant.GrowTime == 0 }
  val size: Signal[Int] = grows.iterate(0)(acc => math.min(Plant.MaxSize, acc + 1))
  val expands: Event[Unit] = size.changedTo(Plant.MaxSize)


  expands += { _ => //#HDL
    // germinate: spawn a new plant in proximity to this one
    world.plan {
      world.board.getPosition(this).foreach { mypos =>
        world.board.nearestFree(mypos).foreach { target =>
          world.spawn(new Plant)
        }
      }
    }
  }


  /** takes amount away from the energy of this plant */
  def takeEnergy(amount: Int) = energy.set(energy.now - amount)
}

class Seed(implicit world: World) extends BoardElement {

  override def isAnimal: Boolean = false

  val growTime = world.time.hour.changed.iterate(Plant.GrowTime)(_ - 1)
  val isDead = growTime map { _ <= 0 } //#SIG

  dies += { _ => //#HDL
    world.board.getPosition(this).foreach { mypos =>
      world.board.nearestFree(mypos).foreach { target =>
        world.spawn(new Plant)
      }
    }
  }
}

class Time {
  val tick = Evt[Unit]()

  val hours = tick.iterate(0)(_ + 1)
  val day = hours map (_ / 24)
  val hour = hours map (_ % 24)
  val week = day map (_ / 7)
  val timestring = Signals.lift(week, day, hour) { (w, d, h) => s"Week: $w Day: $d  hour: $h" }
  override def toString: String = timestring.now
  val newWeek = week.changed
}

