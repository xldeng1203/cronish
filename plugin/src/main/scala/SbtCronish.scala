import sbt._

import Keys._
import complete.DefaultParsers._

import com.github.philcali.cronish.dsl._

object SbtCronish extends Plugin {
 
  object add {
    def > (work: ProcessBuilder) = 
      job (work !) describedAs "process %s".format(work)
    def sh (cmd: String) = 
      job(cmd !) describedAs "sh action %s".format(cmd)
    def sbt (cmd: String, st: State) = job {
      Command.process(cmd, st) 
    } describedAs "sbt action %s".format(cmd)
  }

  val cronishTasks = SettingKey[Seq[Scheduled]]("cronish-tasks", "Actively defined crons.")

  val cronishList = TaskKey[Unit]("cronish-list", "Lists all the active tasks")
  private def cronishListTask = (streams) map { s =>
    Scheduled.active.foreach { sched =>
      val desc = sched.task.description match {
        case Some(str) => str
        case _ => "A job that"
      }
      s.log.info("%s runs %s" format (desc, sched.definition.full))
    }
  }

  val cronishAddSh = InputKey[Unit]("cronish-add-sh", 
              "Adds a cronish task that executes a system command.")

  val cronishAddSbt = InputKey[Unit]("cronish-add-sbt",
              "Adds a sbt task to be executed at a defined interval.")

  private val cronishAddDef = (parsedTask: TaskKey[(String, Seq[Char])]) => {
    (parsedTask, state, streams) map { case ( (es, crons), st, s ) =>
      val cronD = "every%s" format (crons.mkString)

      add sbt (es, st) runs cronD    

      s.log.info("Adding %s to be run %s".format(es, cronD))
    }
  }

  private val generalParser = token(Space ~ "runs" ~ Space) ~> "every" ~> (any +)

  private val cronishParser = (s: State) => {
    val extracted = Project.extract(s)
    import extracted._
    Space flatMap { _ =>
      matched(s.combinedParser) ~ generalParser
    }
  }

  val cronishSettings = Seq (
    cronishTasks := List[Scheduled](),
 
    cronishAddSh <<= inputTask { argTask =>
      (argTask, streams) map { (args, s) =>
        val Array(cmd, crons) = args.mkString(" ").split(" runs ")

        add sh cmd runs crons

        s.log.info("Successfully added task")
      }
    },

    cronishAddSbt <<= InputTask(cronishParser)(cronishAddDef),

    cronishList <<= cronishListTask
  )
}