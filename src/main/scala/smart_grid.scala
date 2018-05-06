

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.math.{min, max}
import scala.io.Source

case class Initialization (initialization_file: Source)
case object Reset

case object Check_power_demand
case class Check_renewable_power_generated (v_wind:Int)
case object Check_possible_power_generated

case class Power_demand(x:Double)
case class Power_renewable(x:Double)
case class Possible_power_generated (power_min:Int,power_max:Int)

case class Data(power:Double, actor:ActorRef, i:Int)

class DataActor extends Actor {

  def receive = {
    case Data(p,actor,i) => {
      val actorName = actor.path.name
      ???
    }

  }
}

class PowerPlant(power_min: Int, power_max:Int) extends Actor  {

  def receive = {
    case Check_possible_power_generated =>
      sender ! Possible_power_generated(power_min,power_max)
  }
}

class Renewable (power_max:Int) extends Actor {
  val coefficient = power_max/6

  def power_generated(v_wind: Int) = {
    val p = (v_wind - 6) * coefficient
    if (v_wind > 6)
      min(p, power_max)
    else
      0
  }

  def receive = {
    case Check_renewable_power_generated(x) =>
      sender ! Power_renewable(power_generated(x))
  }
}

class Client extends Actor {
  def power_consumed = {
    val r = scala.util.Random
    r.nextDouble() + r.nextInt(9)
  }

  def receive = {
    case Check_power_demand => {
      sender ! Power_demand(power_consumed)

    }

  }
}

class Controller extends Actor {
  lazy val power_plants = context.actorSelection("./power_plant*")
  lazy val clients = context.actorSelection("./client*")
  lazy val renewables = context.actorSelection("./renewable*")

  var power_demand = 0
  var power_generated = 0
  var i = 0

  val dataactor = context.actorOf(Props[DataActor],"data_actor")

  def receive = {
    case Initialization(file) => {
      try {
        val datalines = file.getLines()
        val npp = datalines.next().toInt
        val nc = datalines.next().toInt
        val nr = datalines.next().toInt
        for (i <- 1 to npp ) {
          val p = datalines.next().split(" ").map(_.toInt)
          context.actorOf(Props(new PowerPlant(p(0),p(1))), "power_plant" + i)
        }
        for (i <- 1 to nc ) context.actorOf(Props[Client], "client" + i)
        for (i <- 1 to nr ) context.actorOf(Props[Renewable], "renewable" + i)
      } catch {
        case _: java.util.NoSuchElementException =>
          println("Initialization file is formated incorrectly")
      } finally {
        file.close()
      }
    }

    case Reset => {
      power_demand = 0
      power_generated = 0
      i += 1
    }

    case Check_power_demand =>
      clients ! Check_power_demand

    case Power_demand(x) => {
      power_demand += x
      dataactor ! Data(x,sender,i)
    }

    case Check_renewable_power_generated(x) =>
      renewables ! Check_renewable_power_generated(x)

    case Power_renewable(p) => {
      power_generated += p
      dataactor ! Data(p, sender, i)
    }

    case Check_possible_power_generated =>
      power_plants ! Check_possible_power_generated

    case Possible_power_generated(power_min,power_max) => {
      val power_gap = 1.25 * power_demand - power_generated
      if (power_gap > 0) {
        val p = max(min(power_max, power_gap), power_min)
        dataactor ! Data(p, sender, i)
      }
      else
        dataactor ! Data(0, sender, i)
    }
  }
}

object smart_grid extends App {
  try {
    /* Inicjalizacja systemu */
    val initialization_file = Source.fromFile("initialization_data.txt")
    /* Initialization file structure:
    number of power plants
    number of clients
    number of renewables
    npp rowas of power plants characteristics (power_min power_max)
     */
    val system: ActorSystem = ActorSystem("Smart_Grid")
    val controller: ActorRef = system.actorOf(Props[Controller], "Controller_Actor")
    controller ! Initialization(initialization_file)

    /* Pętla dziennego zachowania systemu */
    for (i <- 1 until 365)  {
      val v_wind = scala.util.Random.nextInt(24)
      controller ! Reset
      controller ! Check_power_demand
      controller ! Check_renewable_power_generated(v_wind)

      controller ! Check_possible_power_generated
    }
  } catch {
    case _: java.io.FileNotFoundException =>
      println("Initialization file does not exist")
  }

}
